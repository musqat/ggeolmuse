import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { portfolioApi, type TradingHabitsResponse } from '../../../services/api'
import { HabitsTab } from './HabitsTab'

vi.mock('../../../services/api', () => ({ portfolioApi: { getTradingHabits: vi.fn() } }))
vi.mock('./StatsBarChart', () => ({
  StatsBarChart: ({
    data,
    counts,
    formatValue,
    formatAxis,
  }: {
    data: unknown
    counts?: boolean
    formatValue: (value: number) => string
    formatAxis: (value: number) => string
  }) => (
    <i
      data-testid="bars"
      data-bars={JSON.stringify(data)}
      data-counts={String(!!counts)}
      data-value={formatValue(3)}
      data-axis={formatAxis(2)}
    />
  ),
}))

// 종목은 체결 수 많은 것부터(같으면 체결 금액 큰 것부터), 요일은 월요일부터 7개
const HABITS: TradingHabitsResponse = {
  buyCount: 12,
  sellCount: 4,
  tradingDays: 10,
  averageHoldingDays: 42.5,
  winCount: 3,
  lossCount: 1,
  winRatePercent: 75,
  weekdays: [
    { weekday: 'MONDAY', count: 3 },
    { weekday: 'TUESDAY', count: 0 },
    { weekday: 'WEDNESDAY', count: 5 },
    { weekday: 'THURSDAY', count: 2 },
    { weekday: 'FRIDAY', count: 4 },
    { weekday: 'SATURDAY', count: 2 },
    { weekday: 'SUNDAY', count: 0 },
  ],
  topSymbols: [
    { symbol: 'AAPL', tradeCount: 6, buyCount: 4, sellCount: 2, amountUsd: 2450.3 },
    { symbol: 'TSLA', tradeCount: 6, buyCount: 5, sellCount: 1, amountUsd: 1820 },
    { symbol: 'KO', tradeCount: 4, buyCount: 3, sellCount: 1, amountUsd: 12345.67 },
  ],
}

const EMPTY: TradingHabitsResponse = {
  buyCount: 0,
  sellCount: 0,
  tradingDays: 0,
  averageHoldingDays: null,
  winCount: 0,
  lossCount: 0,
  winRatePercent: null,
  weekdays: HABITS.weekdays.map((day) => ({ ...day, count: 0 })),
  topSymbols: [],
}

function renderTab() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <HabitsTab accountId="all" period="ALL" />
    </QueryClientProvider>
  )
}

const stat = (label: string) => document.querySelector(`[data-stat="${label}"]`)
const rows = (name: string) =>
  within(screen.getByRole('table', { name }))
    .getAllByRole('row')
    .map((row) => [...row.querySelectorAll('th, td')].map((cell) => cell.textContent))

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(portfolioApi.getTradingHabits).mockResolvedValue({ data: HABITS } as never)
})

describe('HabitsTab', () => {
  it('계좌 · 기간 거래 패턴을 불러 위 카드에 매수 · 매도 횟수와 거래한 날을 보인다', async () => {
    renderTab()

    await screen.findByTestId('bars')
    expect(portfolioApi.getTradingHabits).toHaveBeenCalledWith('all', 'ALL')
    expect(stat('거래 횟수')).toHaveTextContent('거래 횟수매수 12회 · 매도 4회')
    expect(stat('거래한 날')).toHaveTextContent('거래한 날10일체결이 있는 거래일')
  })

  it('아래 카드는 평균 보유 기간과 승률 · 승패 수이다', async () => {
    renderTab()

    await screen.findByTestId('bars')
    expect(stat('평균 보유 기간')).toHaveTextContent('평균 보유 기간42.5일먼저 산 주식부터 판 것으로 계산')
    expect(stat('승률')).toHaveTextContent('승률75.00%3승 1패')
  })

  it('매도가 없으면 평균 보유 기간과 승률은 - 이다', async () => {
    vi.mocked(portfolioApi.getTradingHabits).mockResolvedValue({
      data: { ...HABITS, sellCount: 0, averageHoldingDays: null, winCount: 0, lossCount: 0, winRatePercent: null },
    } as never)
    renderTab()

    await screen.findByTestId('bars')
    expect(stat('평균 보유 기간')).toHaveTextContent(/^평균 보유 기간-$/)
    expect(stat('승률')).toHaveTextContent(/^승률-$/)
  })

  it('들고 있던 수량이 없는 매도뿐이면 평균 보유 기간만 - 이고 승률은 그대로 보인다', async () => {
    vi.mocked(portfolioApi.getTradingHabits).mockResolvedValue({
      data: { ...HABITS, sellCount: 1, averageHoldingDays: null, winCount: 0, lossCount: 1, winRatePercent: 0 },
    } as never)
    renderTab()

    await screen.findByTestId('bars')
    expect(stat('평균 보유 기간')).toHaveTextContent(/^평균 보유 기간-$/)
    expect(stat('승률')).toHaveTextContent('승률0.00%0승 1패')
  })

  it('요일별 체결 수를 월 ~ 금과 체결이 있는 토 · 일만 한 색 개수 막대로 그린다', async () => {
    renderTab()

    const bars = await screen.findByTestId('bars')
    expect(screen.getByRole('heading', { name: '요일별 체결' })).toBeInTheDocument()
    expect(bars.dataset.counts).toBe('true')
    expect(bars.dataset.value).toBe('3회')
    expect(bars.dataset.axis).toBe('2')
    expect(JSON.parse(bars.dataset.bars ?? '[]')).toEqual([
      { label: '월', value: 3 },
      { label: '화', value: 0 },
      { label: '수', value: 5 },
      { label: '목', value: 2 },
      { label: '금', value: 4 },
      { label: '토', value: 2 },
    ])
  })

  it('자세히 펼치기는 닫힌 채 종목 수를 보이고, 열면 많이 거래한 종목 표를 보인다', async () => {
    const user = userEvent.setup()
    renderTab()

    const fold = await screen.findByRole('button', { name: /자세히 펼치기/ })
    expect(fold).toHaveAttribute('aria-expanded', 'false')
    expect(fold).toHaveTextContent('자세히 펼치기종목 3개')
    await user.click(fold)

    expect(rows('많이 거래한 종목')).toEqual([
      ['종목', '체결 수', '매수', '매도', '체결 금액 $'],
      ['AAPL', '6', '4', '2', '$2,450.30'],
      ['TSLA', '6', '5', '1', '$1,820.00'],
      ['KO', '4', '3', '1', '$12,345.67'],
    ])
  })

  it('천이 넘는 횟수 · 일수는 카드 · 표에 천 단위 쉼표를 둔다', async () => {
    const user = userEvent.setup()
    vi.mocked(portfolioApi.getTradingHabits).mockResolvedValue({
      data: {
        ...HABITS,
        buyCount: 1_234,
        sellCount: 1_000,
        tradingDays: 1_500,
        winCount: 1_200,
        lossCount: 0,
        topSymbols: [{ symbol: 'AAPL', tradeCount: 2_234, buyCount: 1_234, sellCount: 1_000, amountUsd: 2450.3 }],
      },
    } as never)
    renderTab()

    const fold = await screen.findByRole('button', { name: /자세히 펼치기/ })
    expect(stat('거래 횟수')).toHaveTextContent('거래 횟수매수 1,234회 · 매도 1,000회')
    expect(stat('거래한 날')).toHaveTextContent('거래한 날1,500일')
    expect(stat('승률')).toHaveTextContent('승률75.00%1,200승 0패')
    await user.click(fold)

    expect(rows('많이 거래한 종목')[1]).toEqual(['AAPL', '2,234', '1,234', '1,000', '$2,450.30'])
  })

  it('이 기간에 거래가 없으면 카드 대신 안내 한 줄을 보인다', async () => {
    vi.mocked(portfolioApi.getTradingHabits).mockResolvedValue({ data: EMPTY } as never)
    renderTab()

    expect(await screen.findByText('이 기간에 거래가 없습니다')).toBeInTheDocument()
    expect(document.querySelector('[data-stat]')).toBeNull()
    expect(screen.queryByTestId('bars')).toBeNull()
  })

  it('거래 패턴을 못 받으면 오류와 다시 시도 단추를 보인다', async () => {
    const user = userEvent.setup()
    vi.mocked(portfolioApi.getTradingHabits).mockRejectedValueOnce(new Error('503'))
    renderTab()

    expect(await screen.findByText('거래 패턴을 불러오지 못했습니다')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '다시 시도' }))

    expect(await screen.findByTestId('bars')).toBeInTheDocument()
    expect(portfolioApi.getTradingHabits).toHaveBeenCalledTimes(2)
  })
})
