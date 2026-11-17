import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { accountsApi, portfolioApi } from '../services/api'
import { useAuth } from '../hooks/useAuth'
import Portfolio from './Portfolio'
import { expectRowCards } from '../test/rowCards'

vi.mock('../services/api', () => ({
  portfolioApi: { getAccountPortfolio: vi.fn(), getPortfolioSummary: vi.fn() },
  accountsApi: { getAccountBalance: vi.fn() },
}))
vi.mock('../hooks/useAuth', () => ({ useAuth: vi.fn() }))
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
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } }),
} = {}) {
  vi.mocked(useAuth).mockReturnValue({ isAuthenticated: true } as never)
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

  it('총 자산 이름에 달러로 바꾼 값이라고 적는다', async () => {
    renderPage()

    expect(await screen.findByText('총 자산 (달러 환산)')).toBeInTheDocument()
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

  it('분할을 반영한 보유는 종목 아래에 몇 주가 몇 주로 바뀌었는지 한 줄', async () => {
    vi.mocked(portfolioApi.getAccountPortfolio).mockResolvedValue({
      data: [{ ...HOLDING, splitRatio: 4 }, { ...HOLDING, holdingId: 2, symbol: 'MSFT' }],
    } as never)
    renderPage()

    const table = within(await screen.findByRole('table'))
    const line = await table.findByText('1주→4주 분할 반영')
    expect(line).toHaveClass('truncate', 'whitespace-nowrap')
    expect(table.getAllByText(/분할 반영/)).toHaveLength(1)
  })

  it('휴대폰 너비에서는 보유 종목 표를 줄마다 카드로 보이고 둘째 칸부터 머리줄 이름을 칸 이름으로 둠', async () => {
    renderPage()

    const table = await screen.findByRole('table')
    await within(table).findByText('$90.00')
    expectRowCards(table)
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

  it('손실 보유는 요약 카드 평가손익 · 수익률과 보유 표 손익에 통화 기호 앞 - 와 손실 색', async () => {
    vi.mocked(portfolioApi.getAccountPortfolio).mockResolvedValue({
      data: [{ ...HOLDING, currentValue: 167.7, currentPrice: 83.85, unrealizedPnL: -12.3, returnRate: -6.83 }],
    } as never)
    vi.mocked(portfolioApi.getPortfolioSummary).mockResolvedValue({
      data: { totalCurrentValue: 167.7, totalUnrealizedPnL: -12.3, totalReturnRate: -6.83 },
    } as never)
    renderPage()

    const table = within(await screen.findByRole('table'))
    expect(await table.findByText('-$12.30')).toBeInTheDocument()
    expect(table.getByText('(-6.83%)')).toBeInTheDocument()
    const cards = screen.getAllByText('-$12.30').filter((el) => !el.closest('table'))
    expect(cards).toHaveLength(1)
    expect(cards[0]).toHaveClass('text-loss')
    expect(screen.getByText('-6.83%')).toHaveClass('text-loss')
    expect(screen.queryByText(/$-/)).toBeNull()
  })

  it('보유를 못 받으면 빈 포트폴리오 대신 실패와 다시 시도를 보이고 다시 시도하면 다시 받는다', async () => {
    vi.mocked(portfolioApi.getAccountPortfolio).mockRejectedValue(new Error('down'))
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('포트폴리오를 불러오지 못했습니다')
    expect(screen.queryByText('보유 종목이 없습니다')).toBeNull()

    vi.mocked(portfolioApi.getAccountPortfolio).mockResolvedValue({ data: [HOLDING] } as never)
    await userEvent.click(screen.getByRole('button', { name: '다시 시도' }))

    expect(await screen.findByText('$400.00')).toBeInTheDocument()
  })

  it('요약 카드 · 총 자산은 이 계좌 보유로 셈하고 다른 계좌가 섞인 전체 요약은 받지 않는다', async () => {
    vi.mocked(portfolioApi.getPortfolioSummary).mockResolvedValue({
      data: { totalCurrentValue: 9_999, totalUnrealizedPnL: 999, totalReturnRate: 99 },
    } as never)
    renderPage()

    // 이 계좌 주식 200 + 100 + 135,000 ÷ 1,350
    expect(await screen.findByText('$400.00')).toBeInTheDocument()
    expect(screen.getByText('+11.11%')).toBeInTheDocument()
    expect(portfolioApi.getPortfolioSummary).not.toHaveBeenCalled()
  })

  it('현재가 없는 종목은 총 자산 · 평가손익 · 수익률에서 빼고 그 종목을 알린다', async () => {
    vi.mocked(portfolioApi.getAccountPortfolio).mockResolvedValue({
      data: [HOLDING, { holdingId: 2, symbol: 'MSFT', totalQuantity: 5, totalInvestedAmount: 500, avgPurchasePrice: 100 }],
    } as never)
    renderPage()

    expect(await screen.findByText('$400.00')).toBeInTheDocument()
    // 손익 20 ÷ 현재가 있는 종목 투자 180
    expect(screen.getByText('+11.11%')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('현재가가 없는 종목(MSFT)은 총 자산 · 평가손익 · 수익률에서 뺐습니다')
  })
})
