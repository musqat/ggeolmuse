import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { portfolioApi, type DividendStatsResponse } from '../../../services/api'
import { DividendsTab } from './DividendsTab'

vi.mock('../../../services/api', () => ({ portfolioApi: { getDividendStats: vi.fn() } }))
vi.mock('./StatsBarChart', () => ({
  StatsBarChart: ({ data }: { data: unknown }) => <i data-testid="bars" data-bars={JSON.stringify(data)} />,
}))

// 종목은 세후 원화 큰 것부터, 지급 내역은 최근 배당락일부터
const DIVIDENDS: DividendStatsResponse = {
  netKrw: 85_000,
  netUsd: 63.2,
  grossUsd: 74.35,
  taxUsd: 11.15,
  payoutCount: 7,
  monthly: [
    { month: '2024-09', netUsd: 12.4, netKrw: 16_500 },
    { month: '2024-10', netUsd: 50.8, netKrw: 68_500 },
  ],
  symbols: [
    { symbol: 'KO', netUsd: 30.1, netKrw: 40_100, payoutCount: 3, sharePercent: 47.18 },
    { symbol: 'PEP', netUsd: 22.3, netKrw: 30_000, payoutCount: 2, sharePercent: 35.29 },
    { symbol: 'T', netUsd: 10.8, netKrw: 14_900, payoutCount: 2, sharePercent: 17.53 },
  ],
  payouts: [
    {
      exDate: '2024-10-15', accountId: 3, symbol: 'PEP', shares: 5, dividendPerShareUsd: 1.355,
      grossUsd: 6.78, taxUsd: 1.02, netUsd: 5.76, fxRate: 1350.25, netKrw: 7_777,
    },
    {
      exDate: '2024-09-13', accountId: 3, symbol: 'KO', shares: 10.5, dividendPerShareUsd: 0.485,
      grossUsd: 5.09, taxUsd: 0.76, netUsd: 4.33, fxRate: null, netKrw: 0,
    },
  ],
}

const EMPTY: DividendStatsResponse = {
  netKrw: 0, netUsd: 0, grossUsd: 0, taxUsd: 0, payoutCount: 0, monthly: [], symbols: [], payouts: [],
}

// 배당락일 환율이 없는 KO 배당 하나와 환율이 있는 PEP 배당 하나
const PARTLY_NO_RATE: DividendStatsResponse = {
  netKrw: 7_777,
  netUsd: 10.09,
  grossUsd: 11.87,
  taxUsd: 1.78,
  payoutCount: 2,
  monthly: [
    { month: '2024-09', netUsd: 4.33, netKrw: 0 },
    { month: '2024-10', netUsd: 5.76, netKrw: 7_777 },
  ],
  symbols: [
    { symbol: 'PEP', netUsd: 5.76, netKrw: 7_777, payoutCount: 1, sharePercent: 100 },
    { symbol: 'KO', netUsd: 4.33, netKrw: 0, payoutCount: 1, sharePercent: 0 },
  ],
  payouts: DIVIDENDS.payouts,
}

const NAMES = { 3: '연습', 5: '배당' }

function renderTab(accountId: number | 'all' = 'all') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <DividendsTab accountId={accountId} period="ALL" accountNames={NAMES} />
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
  vi.mocked(portfolioApi.getDividendStats).mockResolvedValue({ data: DIVIDENDS } as never)
})

describe('DividendsTab', () => {
  it('계좌 · 기간 배당을 불러 위 카드에 세후 원화 · 달러 배당과 지급 횟수 · 종목 수를 보인다', async () => {
    renderTab()

    await screen.findByTestId('bars')
    expect(portfolioApi.getDividendStats).toHaveBeenCalledWith('all', 'ALL')
    expect(stat('받은 배당')).toHaveTextContent('받은 배당세후₩85,000$63.20')
    expect(stat('지급 횟수')).toHaveTextContent('지급 횟수7회종목 3개')
  })

  it('아래 카드는 가장 많이 받은 종목의 이름 · 원화 · 비율과 원천징수 세금 · 세전 배당이다', async () => {
    renderTab()

    await screen.findByTestId('bars')
    expect(stat('가장 많이 받은 종목')).toHaveTextContent('가장 많이 받은 종목KO₩40,100(47.18%)')
    expect(stat('가장 많이 받은 종목')).not.toHaveTextContent('+')
    expect(stat('원천징수 세금')).toHaveTextContent('원천징수 세금$11.15세전 $74.35')
  })

  it('배당 원화가 모두 0 이면 가장 많이 받은 종목 칸은 - 이다', async () => {
    vi.mocked(portfolioApi.getDividendStats).mockResolvedValue({
      data: {
        ...DIVIDENDS,
        netKrw: 0,
        symbols: [{ symbol: 'KO', netUsd: 4.33, netKrw: 0, payoutCount: 1, sharePercent: 0 }],
        payouts: [DIVIDENDS.payouts[1]],
      },
    } as never)
    renderTab()

    await screen.findByTestId('bars')
    expect(stat('가장 많이 받은 종목')).toHaveTextContent(/^가장 많이 받은 종목-$/)
  })

  it('달마다 세후 원화 배당을 막대로 그린다', async () => {
    renderTab()

    const bars = await screen.findByTestId('bars')
    expect(screen.getByRole('heading', { name: '달마다 배당' })).toBeInTheDocument()
    expect(JSON.parse(bars.dataset.bars ?? '[]')).toEqual([
      { label: '2024-09', value: 16_500 },
      { label: '2024-10', value: 68_500 },
    ])
  })

  it('자세히 펼치기를 열면 종목별 표와 지급 내역 표를 보이고 환율이 없는 배당은 환율 · 세후 원화가 - 이다', async () => {
    const user = userEvent.setup()
    renderTab()

    const fold = await screen.findByRole('button', { name: /자세히 펼치기/ })
    expect(fold).toHaveAttribute('aria-expanded', 'false')
    await user.click(fold)

    expect(rows('종목별 배당')).toEqual([
      ['종목', '횟수', '세후 $', '세후 ₩', '비율'],
      ['KO', '3', '$30.10', '₩40,100', '47.18%'],
      ['PEP', '2', '$22.30', '₩30,000', '35.29%'],
      ['T', '2', '$10.80', '₩14,900', '17.53%'],
    ])
    expect(rows('지급 내역')).toEqual([
      ['배당락일', '계좌', '종목', '수량', '주당 $', '세전 $', '세금 $', '세후 $', '환율', '세후 ₩'],
      ['2024-10-15', '연습', 'PEP', '5', '$1.355', '$6.78', '$1.02', '$5.76', '₩1,350.25', '₩7,777'],
      ['2024-09-13', '연습', 'KO', '10.5', '$0.485', '$5.09', '$0.76', '$4.33', '-', '-'],
    ])
  })

  it('계좌 하나면 지급 내역 표에 계좌 칸이 없다', async () => {
    const user = userEvent.setup()
    renderTab(3)

    await user.click(await screen.findByRole('button', { name: /자세히 펼치기/ }))

    expect(portfolioApi.getDividendStats).toHaveBeenCalledWith(3, 'ALL')
    expect(rows('지급 내역')[0]).toEqual(['배당락일', '종목', '수량', '주당 $', '세전 $', '세금 $', '세후 $', '환율', '세후 ₩'])
  })

  it('배당락일 환율이 없는 배당 수를 카드 아래에 보이고 환율이 하나도 없는 종목 · 달은 원화를 비운다', async () => {
    const user = userEvent.setup()
    vi.mocked(portfolioApi.getDividendStats).mockResolvedValue({ data: PARTLY_NO_RATE } as never)
    renderTab()

    const bars = await screen.findByTestId('bars')
    expect(stat('받은 배당')).toHaveTextContent('받은 배당세후₩7,777$10.09')
    expect(screen.getByText('배당락일 환율이 없는 배당 1건은 원화 합에 들지 않음')).toBeInTheDocument()
    expect(JSON.parse(bars.dataset.bars ?? '[]')).toEqual([{ label: '2024-10', value: 7_777 }])
    await user.click(screen.getByRole('button', { name: /자세히 펼치기/ }))
    expect(rows('종목별 배당')).toEqual([
      ['종목', '횟수', '세후 $', '세후 ₩', '비율'],
      ['PEP', '1', '$5.76', '₩7,777', '100.00%'],
      ['KO', '1', '$4.33', '-', '-'],
    ])
  })

  it('배당락일 환율이 모두 없으면 받은 배당 원화는 - 이고 달마다 막대를 그리지 않는다', async () => {
    vi.mocked(portfolioApi.getDividendStats).mockResolvedValue({
      data: {
        ...PARTLY_NO_RATE,
        netKrw: 0,
        netUsd: 4.33,
        grossUsd: 5.09,
        taxUsd: 0.76,
        payoutCount: 1,
        monthly: [PARTLY_NO_RATE.monthly[0]],
        symbols: [PARTLY_NO_RATE.symbols[1]],
        payouts: [DIVIDENDS.payouts[1]],
      },
    } as never)
    renderTab()

    expect(await screen.findByText('배당락일 환율이 없는 배당 1건은 원화 합에 들지 않음')).toBeInTheDocument()
    expect(stat('받은 배당')).toHaveTextContent('받은 배당세후-$4.33')
    expect(stat('가장 많이 받은 종목')).toHaveTextContent(/^가장 많이 받은 종목-$/)
    expect(screen.queryByRole('heading', { name: '달마다 배당' })).toBeNull()
    expect(screen.queryByTestId('bars')).toBeNull()
  })

  it('기간을 바꿔 다시 불러오는 동안에도 열어 둔 자세히 펼치기는 열린 채 새 내역으로 바뀐다', async () => {
    const user = userEvent.setup()
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const tab = (period: '1M' | 'ALL') => (
      <QueryClientProvider client={client}>
        <DividendsTab accountId="all" period={period} accountNames={NAMES} />
      </QueryClientProvider>
    )
    const { rerender } = render(tab('ALL'))
    await user.click(await screen.findByRole('button', { name: /자세히 펼치기/ }))
    let resolve: (value: unknown) => void = () => {}
    vi.mocked(portfolioApi.getDividendStats).mockReturnValueOnce(new Promise((done) => (resolve = done)) as never)

    rerender(tab('1M'))

    expect(portfolioApi.getDividendStats).toHaveBeenLastCalledWith('all', '1M')
    expect(screen.getByRole('button', { name: /자세히 펼치기/ })).toHaveAttribute('aria-expanded', 'true')
    resolve({ data: { ...DIVIDENDS, payouts: [DIVIDENDS.payouts[0]] } })
    await waitFor(() => expect(rows('지급 내역')).toHaveLength(2))
    expect(screen.getByRole('button', { name: /자세히 펼치기/ })).toHaveAttribute('aria-expanded', 'true')
  })

  it('이 기간에 배당이 없으면 카드 대신 안내 한 줄을 보인다', async () => {
    vi.mocked(portfolioApi.getDividendStats).mockResolvedValue({ data: EMPTY } as never)
    renderTab()

    expect(await screen.findByText('이 기간에 받은 배당이 없습니다')).toBeInTheDocument()
    expect(document.querySelector('[data-stat]')).toBeNull()
    expect(screen.queryByTestId('bars')).toBeNull()
  })

  it('배당을 못 받으면 오류와 다시 시도 단추를 보인다', async () => {
    const user = userEvent.setup()
    vi.mocked(portfolioApi.getDividendStats).mockRejectedValueOnce(new Error('503'))
    renderTab()

    expect(await screen.findByText('배당을 불러오지 못했습니다')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '다시 시도' }))

    expect(await screen.findByTestId('bars')).toBeInTheDocument()
    expect(portfolioApi.getDividendStats).toHaveBeenCalledTimes(2)
  })
})
