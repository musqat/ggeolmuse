import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import {
  accountsApi,
  portfolioApi,
  type ProfitDetailResponse,
  type ProfitFlowResponse,
  type SymbolContributionResponse,
} from '../services/api'
import { useAuth } from '../hooks/useAuth'
import AccountStats from './AccountStats'

vi.mock('recharts', () => import('../test/rechartsProbe'))
vi.mock('../services/api', () => ({
  accountsApi: { getAccounts: vi.fn(), getAccountBalance: vi.fn() },
  portfolioApi: {
    getPortfolioSummary: vi.fn(),
    getProfitFlow: vi.fn(),
    getProfitDetail: vi.fn(),
    getSymbolContributions: vi.fn(),
  },
}))
vi.mock('../components/account/stats/StatsBarChart', () => ({ StatsBarChart: () => <i data-testid="bars" /> }))
vi.mock('../hooks/useAuth', () => ({ useAuth: vi.fn() }))
vi.mock('../hooks/useAuthModal', () => ({ useAuthModal: () => ({ openLogin: vi.fn(), openSignup: vi.fn() }) }))

const FLOW: ProfitFlowResponse = {
  points: [
    { date: '2026-07-01', profitKrw: 100_000, investedKrw: 2_000_000 },
    { date: '2026-07-02', profitKrw: 400_000, investedKrw: 2_000_000 },
    { date: '2026-07-03', profitKrw: -50_000, investedKrw: 2_500_000 },
    { date: '2026-07-06', profitKrw: 250_000, investedKrw: 2_500_000 },
  ],
  best: { date: '2026-07-02', profitKrw: 400_000, investedKrw: 2_000_000 },
  worst: { date: '2026-07-03', profitKrw: -50_000, investedKrw: 2_500_000 },
  currentProfitKrw: 250_000,
}

const EMPTY: ProfitFlowResponse = {
  points: [], best: null, worst: null, currentProfitKrw: 0,
}

const DETAIL: ProfitDetailResponse = {
  monthly: [{ month: '2026-07', profitKrw: 250_000 }],
  realizedKrw: 0,
  unrealizedKrw: 230_000,
  sales: [],
  holdings: [
    {
      symbol: 'AAPL', quantity: 10, averagePriceUsd: 180, closeUsd: 198.5, closeDate: '2026-07-06',
      unrealizedUsd: 185, unrealizedKrw: 230_000, returnPercent: 10.28,
    },
  ],
}

const SYMBOLS: SymbolContributionResponse = {
  totalKrw: 250_000,
  symbols: [
    {
      symbol: 'AAPL', realizedKrw: 0, unrealizedKrw: 250_000, dividendKrw: 0, feeKrw: 0,
      totalKrw: 250_000, sharePercent: 100, held: true,
    },
  ],
}

const BALANCES: Record<number, { balanceKrw: number; balanceUsd: number }> = {
  3: { balanceKrw: 1_000_000, balanceUsd: 100 },
  5: { balanceKrw: 500_000, balanceUsd: 0 },
}

function renderPage(isAuthenticated = true) {
  vi.mocked(useAuth).mockReturnValue({ isAuthenticated } as never)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <AccountStats />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

const stat = (label: string) => document.querySelector(`[data-stat="${label}"]`)
const statLabels = () => [...document.querySelectorAll('[data-stat]')].map((element) => element.getAttribute('data-stat'))

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(accountsApi.getAccounts).mockResolvedValue({
    data: [{ accountId: 3, accountName: '연습' }, { accountId: 5, accountName: '배당' }],
  } as never)
  vi.mocked(accountsApi.getAccountBalance).mockImplementation(
    async (id: number) => ({ data: { ...BALANCES[id], currentExchangeRate: 1350 } }) as never
  )
  vi.mocked(portfolioApi.getPortfolioSummary).mockResolvedValue({
    data: {
      totalCurrentValue: 250,
      holdings: [
        { accountId: 3, currentValue: 200 },
        { accountId: 5, currentValue: 50 },
      ],
    },
  } as never)
  vi.mocked(portfolioApi.getProfitFlow).mockResolvedValue({ data: FLOW } as never)
  vi.mocked(portfolioApi.getProfitDetail).mockResolvedValue({ data: DETAIL } as never)
  vi.mocked(portfolioApi.getSymbolContributions).mockResolvedValue({ data: SYMBOLS } as never)
})

describe('AccountStats', () => {
  it('로그인 안 했으면 로그인 안내를 보이고 수익 흐름을 묻지 않는다', () => {
    renderPage(false)

    expect(screen.getByText('계좌 통계를 보시려면 먼저 로그인해주세요')).toBeInTheDocument()
    expect(portfolioApi.getProfitFlow).not.toHaveBeenCalled()
  })

  it('처음에는 전체 계좌 · 전체 기간 수익 흐름을 불러 총자산 · 현재 수익, 최고 · 최저 수익을 그 날짜 원금 대비 % 와 함께 보인다', async () => {
    renderPage()

    expect(await screen.findByText('+₩250,000')).toBeInTheDocument()
    expect(portfolioApi.getProfitFlow).toHaveBeenCalledWith('all', 'ALL')
    expect(statLabels()).toEqual(['오늘 총자산', '현재 수익', '최고 수익', '최저 수익'])
    expect(stat('현재 수익')).toHaveTextContent('현재 수익+₩250,000(10.00%)2026-07-06 기준 · 원금 ₩2,500,000')
    expect(stat('최고 수익')).toHaveTextContent('최고 수익+₩400,000(20.00%)2026-07-02 · 원금 ₩2,000,000')
    expect(stat('최저 수익')).toHaveTextContent('최저 수익-₩50,000(-2.00%)2026-07-03 · 원금 ₩2,500,000')
    expect(screen.getByRole('tab', { name: '수익' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('button', { name: '전체' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('오늘 총자산은 전체 계좌면 계좌 화면 총자산과 계좌 수, 계좌 하나면 그 계좌 현금과 보유 평가액과 계좌 이름이다', async () => {
    const user = userEvent.setup()
    renderPage()

    // 1,000,000 + 100 × 1,350 + 500,000 + 250 × 1,350
    await waitFor(() => expect(stat('오늘 총자산')).toHaveTextContent('오늘 총자산2개 계좌₩1,972,500현금 + 보유 평가액'))
    await user.selectOptions(screen.getByLabelText('계좌'), '연습')

    // 1,000,000 + 100 × 1,350 + 200 × 1,350
    expect(stat('오늘 총자산')).toHaveTextContent('오늘 총자산연습₩1,405,000현금 + 보유 평가액')
    await waitFor(() => expect(portfolioApi.getProfitFlow).toHaveBeenLastCalledWith(3, 'ALL'))
  })

  it('그 날짜에 들고 있던 종목이 없으면(원금 0) % 와 원금 없이 날짜만 보인다', async () => {
    vi.mocked(portfolioApi.getProfitFlow).mockResolvedValue({
      data: { ...FLOW, worst: { date: '2026-07-03', profitKrw: -50_000, investedKrw: 0 } },
    } as never)
    renderPage()

    await waitFor(() => expect(stat('최저 수익')).toHaveTextContent('최저 수익-₩50,0002026-07-03'))
    expect(stat('최저 수익')).not.toHaveTextContent('%')
    expect(stat('최저 수익')).not.toHaveTextContent('원금')
  })

  it('기간을 바꾸면 그 기간으로 다시 묻는다', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('+₩250,000')

    await user.click(screen.getByRole('button', { name: '1달' }))

    expect(screen.getByRole('button', { name: '1달' })).toHaveAttribute('aria-pressed', 'true')
    await waitFor(() => expect(portfolioApi.getProfitFlow).toHaveBeenLastCalledWith('all', '1M'))
  })

  it('수익 흐름이 없으면 그래프 대신 거래 화면으로 가는 안내를 보인다', async () => {
    vi.mocked(portfolioApi.getProfitFlow).mockResolvedValue({ data: EMPTY } as never)
    renderPage()

    expect(await screen.findByText('아직 수익 흐름이 없습니다')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '거래하러 가기' })).toHaveAttribute('href', '/trading')
    expect(screen.queryByTestId('line-chart')).toBeNull()
    expect(stat('현재 수익')).toHaveTextContent(/^현재 수익-$/)
    expect(stat('최고 수익')).toHaveTextContent(/^최고 수익-$/)
    expect(stat('최저 수익')).toHaveTextContent(/^최저 수익-$/)
  })

  it('수익 흐름을 못 받으면 오류와 다시 시도 단추를 보인다', async () => {
    const user = userEvent.setup()
    vi.mocked(portfolioApi.getProfitFlow).mockRejectedValueOnce(new Error('503'))
    renderPage()

    expect(await screen.findByText('수익 흐름을 불러오지 못했습니다')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '다시 시도' }))

    expect(await screen.findByText('+₩250,000')).toBeInTheDocument()
    expect(portfolioApi.getProfitFlow).toHaveBeenCalledTimes(2)
  })

  it('주제 탭은 수익 · 종목이고 처음에는 수익 패널만 열어 수익 내역만 묻는다', async () => {
    renderPage()

    const panel = await screen.findByRole('tabpanel')
    expect(screen.getAllByRole('tab').map((tab) => tab.textContent)).toEqual(['수익', '종목'])
    expect(screen.getByRole('tab', { name: '수익' })).toHaveAttribute('tabindex', '0')
    expect(screen.getByRole('tab', { name: '종목' })).toHaveAttribute('tabindex', '-1')
    expect(screen.getByRole('tab', { name: '종목' })).toHaveAttribute('aria-selected', 'false')
    expect(panel).toHaveAttribute('id', 'stats-panel-profit')
    expect(panel).toHaveAttribute('aria-labelledby', 'stats-tab-profit')
    expect(panel).toHaveAttribute('tabindex', '0')
    await waitFor(() => expect(portfolioApi.getProfitDetail).toHaveBeenCalledWith('all', 'ALL'))
    expect(portfolioApi.getSymbolContributions).not.toHaveBeenCalled()
  })

  it('종목 탭을 누르면 종목 패널로 바뀌어 종목별 기여를 묻고 수익 흐름 그래프는 그대로 보인다', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('+₩250,000')

    await user.click(screen.getByRole('tab', { name: '종목' }))

    expect(screen.getByRole('tab', { name: '종목' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: '수익' })).toHaveAttribute('aria-selected', 'false')
    expect(screen.getByRole('tabpanel')).toHaveAttribute('id', 'stats-panel-symbols')
    await waitFor(() => expect(stat('기간 수익')).toHaveTextContent('기간 수익+₩250,000'))
    expect(portfolioApi.getSymbolContributions).toHaveBeenCalledWith('all', 'ALL')
    expect(screen.getByTestId('line-chart')).toBeInTheDocument()
    expect(stat('오늘 총자산')).toBeNull()
  })

  it('왼쪽 · 오른쪽 화살표로 옆 탭을 고르고 초점도 그 탭으로 옮긴다', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('+₩250,000')
    screen.getByRole('tab', { name: '수익' }).focus()

    await user.keyboard('{ArrowRight}')
    expect(screen.getByRole('tab', { name: '종목' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: '종목' })).toHaveFocus()

    await user.keyboard('{ArrowRight}')
    expect(screen.getByRole('tab', { name: '수익' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: '수익' })).toHaveFocus()

    await user.keyboard('{ArrowLeft}')
    expect(screen.getByRole('tab', { name: '종목' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: '종목' })).toHaveFocus()
  })

  it('기간을 바꾸면 고른 탭도 그 기간으로 다시 묻는다', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('tab', { name: '종목' }))

    await user.click(screen.getByRole('button', { name: '1달' }))

    await waitFor(() => expect(portfolioApi.getSymbolContributions).toHaveBeenLastCalledWith('all', '1M'))
    expect(portfolioApi.getProfitFlow).toHaveBeenLastCalledWith('all', '1M')
  })

  it('계좌 관리로 돌아가는 링크가 있다', async () => {
    renderPage()

    expect(await screen.findByRole('link', { name: '계좌 관리' })).toHaveAttribute('href', '/account')
  })
})
