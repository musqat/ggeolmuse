import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { accountsApi, portfolioApi } from '../services/api'
import { useAuth } from '../hooks/useAuth'
import Portfolio from './Portfolio'

vi.mock('../services/api', () => ({
  portfolioApi: { getAccountPortfolio: vi.fn(), getPortfolioSummary: vi.fn() },
  accountsApi: { getAccountBalance: vi.fn() },
}))
vi.mock('../hooks/useAuth', () => ({ useAuth: vi.fn() }))
vi.mock('../hooks/useAuthModal', () => ({ useAuthModal: () => ({ openLogin: vi.fn(), openSignup: vi.fn() }) }))
vi.mock('../components/charts/portfolio/PortfolioPieChart', () => ({
  default: ({ data }: { data: { symbol: string; color: string }[] }) => (
    <p>파이 {data.map((d) => `${d.symbol}:${d.color}`).join(',')}</p>
  ),
}))

const HOLDING = {
  holdingId: 1, symbol: 'AAPL', totalQuantity: 2, currentValue: 200, totalInvestedAmount: 180,
  currentPrice: 100, avgPurchasePrice: 90, unrealizedPnL: 20, returnRate: 11.11,
}
const BALANCE = { accountName: '연습', balanceKrw: 135_000, balanceUsd: 100, currentExchangeRate: 1350 }

// 이동한 경로
function Where() {
  return <p>{useLocation().pathname}</p>
}

function renderPage({
  path = '/portfolio?accountId=3',
  isAuthenticated = true,
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } }),
} = {}) {
  vi.mocked(useAuth).mockReturnValue({ isAuthenticated } as never)
  const view = render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/portfolio" element={<Portfolio />} />
          <Route path="*" element={<Where />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
  return { client, unmount: view.unmount }
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(portfolioApi.getAccountPortfolio).mockResolvedValue({ data: [HOLDING] } as never)
  vi.mocked(portfolioApi.getPortfolioSummary).mockResolvedValue({
    data: { totalCurrentValue: 200, totalUnrealizedPnL: 20, totalReturnRate: 11.11 },
  } as never)
  vi.mocked(accountsApi.getAccountBalance).mockResolvedValue({ data: BALANCE } as never)
})

describe('Portfolio', () => {
  it('로그인 안 했으면 로그인 안내를 보인다', () => {
    renderPage({ isAuthenticated: false })

    expect(screen.getByText('포트폴리오 기능을 이용하시려면 먼저 로그인해주세요')).toBeInTheDocument()
    expect(portfolioApi.getAccountPortfolio).not.toHaveBeenCalled()
  })

  it('계좌를 안 골랐으면 계좌 관리로 보낸다', async () => {
    const user = userEvent.setup()
    renderPage({ path: '/portfolio' })

    await user.click(screen.getByRole('button', { name: '계좌 관리로 이동' }))

    expect(screen.getByText('/account')).toBeInTheDocument()
  })

  it('총 자산은 주식 평가액 + 달러 현금 + 원화 현금 ÷ 환율이다', async () => {
    renderPage()

    // 200 + 100 + 135,000 ÷ 1,350
    expect(await screen.findByText('$400.00')).toBeInTheDocument()
  })

  it('다른 화면에 갔다 바로 돌아와도 받아 둔 환율로 총 자산을 계산한다', async () => {
    const { client, unmount } = renderPage()
    expect(await screen.findByText('$400.00')).toBeInTheDocument()
    unmount()

    renderPage({ client })

    expect(await screen.findByText('$400.00')).toBeInTheDocument()
    expect(accountsApi.getAccountBalance).toHaveBeenCalledTimes(1)
  })

  it('자산 배분은 금액 큰 순으로 색을 주고 비율을 보인다', async () => {
    renderPage()

    expect(await screen.findByText('파이 AAPL:var(--alloc-1),USD 현금:var(--alloc-2),KRW 현금:var(--alloc-3)')).toBeInTheDocument()
    expect(screen.getByText('50.0%')).toBeInTheDocument()
    expect(screen.getAllByText('25.0%')).toHaveLength(2)
  })

  it('보유 종목 표에 평균단가 · 현재가 · 평가금액 · 손익을 보인다', async () => {
    renderPage()

    const table = within(await screen.findByRole('table'))
    expect(await table.findByText('$90.00')).toBeInTheDocument()
    expect(table.getByText('$100.00')).toBeInTheDocument()
    expect(table.getByText('$200.00')).toBeInTheDocument()
    expect(table.getByText('+$20.00')).toBeInTheDocument()
    expect(table.getByText('(+11.11%)')).toBeInTheDocument()
  })

  it('보유도 현금도 없으면 비었다고 보인다', async () => {
    vi.mocked(portfolioApi.getAccountPortfolio).mockResolvedValue({ data: [] } as never)
    vi.mocked(accountsApi.getAccountBalance).mockResolvedValue({
      data: { ...BALANCE, balanceKrw: 0, balanceUsd: 0 },
    } as never)
    renderPage()

    expect(await screen.findByText('보유 자산이 없습니다')).toBeInTheDocument()
    expect(screen.getByText('보유 종목이 없습니다')).toBeInTheDocument()
  })
})
