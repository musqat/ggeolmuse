import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { portfolioApi, type ProfitDetailResponse, type ProfitFlowResponse } from '../../../services/api'
import { ProfitTab } from './ProfitTab'

vi.mock('../../../services/api', () => ({ portfolioApi: { getProfitDetail: vi.fn() } }))
vi.mock('./StatsBarChart', () => ({
  StatsBarChart: ({ data }: { data: unknown }) => <i data-testid="bars" data-bars={JSON.stringify(data)} />,
}))

const FLOW: ProfitFlowResponse = {
  points: [
    { date: '2024-09-02', profitKrw: 10_000, investedKrw: 1_000_000 },
    { date: '2024-11-08', profitKrw: 160_000, investedKrw: 1_200_000 },
  ],
  best: { date: '2024-11-08', profitKrw: 160_000, investedKrw: 1_200_000 },
  worst: { date: '2024-09-02', profitKrw: 10_000, investedKrw: 1_000_000 },
  currentProfitKrw: 160_000,
}

const DETAIL: ProfitDetailResponse = {
  monthly: [
    { month: '2024-09', profitKrw: 120_000 },
    { month: '2024-10', profitKrw: -30_000 },
  ],
  realizedKrw: 46_834,
  unrealizedKrw: 121_680,
  sales: [
    {
      tradeId: 41, accountId: 3, date: '2024-09-18', symbol: 'AAPL', quantity: 2, priceUsd: 220.15,
      realizedUsd: 35.2, fxRate: 1330.5, realizedKrw: 46_834, returnPercent: 8.69,
    },
    {
      tradeId: 40, accountId: 3, date: '2024-09-02', symbol: 'TSLA', quantity: 0.5, priceUsd: 200,
      realizedUsd: -10.5, fxRate: null, realizedKrw: 0, returnPercent: -9.5,
    },
  ],
  holdings: [
    {
      symbol: 'AAPL', quantity: 3, averagePriceUsd: 180, closeUsd: 225.4, closeDate: '2024-11-08',
      unrealizedUsd: 136.2, unrealizedKrw: 190_680, returnPercent: 25.22,
    },
    {
      symbol: 'XYZ', quantity: 1.25, averagePriceUsd: 40, closeUsd: null, closeDate: null,
      unrealizedUsd: -50, unrealizedKrw: -69_000, returnPercent: -100,
    },
  ],
}

const EMPTY: ProfitDetailResponse = { monthly: [], realizedKrw: 0, unrealizedKrw: 0, sales: [], holdings: [] }

const NAMES = { 3: '연습', 5: '배당' }

function renderTab(accountId: number | 'all' = 'all') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ProfitTab accountId={accountId} period="ALL" flow={FLOW} assets={1_972_500} scope="2개 계좌" accountNames={NAMES} />
    </QueryClientProvider>
  )
}

const rows = (name: string) =>
  within(screen.getByRole('table', { name }))
    .getAllByRole('row')
    .map((row) => [...row.querySelectorAll('th, td')].map((cell) => cell.textContent))

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(portfolioApi.getProfitDetail).mockResolvedValue({ data: DETAIL } as never)
})

describe('ProfitTab', () => {
  it('위 카드는 오늘 총자산 · 현재 수익, 아래 카드는 최고 · 최저 수익이다', async () => {
    renderTab()

    const labels = () => [...document.querySelectorAll('[data-stat]')].map((element) => element.getAttribute('data-stat'))
    expect(labels()).toEqual(['오늘 총자산', '현재 수익', '최고 수익', '최저 수익'])
    expect(document.querySelector('[data-stat="현재 수익"]')).toHaveTextContent(
      '현재 수익+₩160,000(+13.33%)2024-11-08 기준 · 원금 ₩1,200,000'
    )
    await screen.findByTestId('bars')
  })

  it('계좌 · 기간 수익 내역을 불러 달마다 수익을 막대로 그린다', async () => {
    renderTab()

    const bars = await screen.findByTestId('bars')
    expect(portfolioApi.getProfitDetail).toHaveBeenCalledWith('all', 'ALL')
    expect(screen.getByRole('heading', { name: '달마다 수익' })).toBeInTheDocument()
    expect(JSON.parse(bars.dataset.bars ?? '[]')).toEqual([
      { label: '2024-09', value: 120_000 },
      { label: '2024-10', value: -30_000 },
    ])
  })

  it('자세히 펼치기는 닫힌 채 실현 · 평가 합을 보이고, 열면 판 거래와 들고 있는 종목 표를 보인다', async () => {
    const user = userEvent.setup()
    renderTab()

    const fold = await screen.findByRole('button', { name: /자세히 펼치기/ })
    expect(fold).toHaveAttribute('aria-expanded', 'false')
    expect(fold).toHaveTextContent('실현 +₩46,834 · 평가 +₩121,680')
    expect(screen.queryByRole('table')).toBeNull()

    await user.click(fold)

    expect(fold).toHaveAttribute('aria-expanded', 'true')
    expect(rows('판 거래')).toEqual([
      ['날짜', '계좌', '종목', '수량', '판 가격 $', '실현 손익 $', '실현 손익 ₩', '수익률'],
      ['2024-09-18', '연습', 'AAPL', '2', '$220.15', '+$35.20', '+₩46,834', '+8.69%'],
      ['2024-09-02', '연습', 'TSLA', '0.5', '$200.00', '-$10.50', '-', '-9.50%'],
    ])
    expect(rows('들고 있는 종목')).toEqual([
      ['종목', '수량', '평균 단가 $', '종가 $', '평가 손익 $', '평가 손익 ₩', '수익률'],
      ['AAPL', '3', '$180.00', '$225.402024-11-08', '+$136.20', '+₩190,680', '+25.22%'],
      ['XYZ', '1.25', '$40.00', '-', '-$50.00', '-₩69,000', '-100.00%'],
    ])
  })

  it('기간을 바꿔 다시 불러오는 동안에도 열어 둔 자세히 펼치기는 열린 채 새 내역으로 바뀐다', async () => {
    const user = userEvent.setup()
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const tab = (period: '1M' | 'ALL') => (
      <QueryClientProvider client={client}>
        <ProfitTab accountId="all" period={period} flow={FLOW} assets={1_972_500} scope="2개 계좌" accountNames={NAMES} />
      </QueryClientProvider>
    )
    const { rerender } = render(tab('ALL'))
    await user.click(await screen.findByRole('button', { name: /자세히 펼치기/ }))
    let resolve: (value: unknown) => void = () => {}
    vi.mocked(portfolioApi.getProfitDetail).mockReturnValueOnce(new Promise((done) => (resolve = done)) as never)

    rerender(tab('1M'))

    expect(portfolioApi.getProfitDetail).toHaveBeenLastCalledWith('all', '1M')
    expect(screen.getByRole('button', { name: /자세히 펼치기/ })).toHaveAttribute('aria-expanded', 'true')
    resolve({ data: { ...DETAIL, sales: [] } })
    expect(await screen.findByText('이 기간에 판 거래가 없습니다')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /자세히 펼치기/ })).toHaveAttribute('aria-expanded', 'true')
  })

  it('판 거래 · 들고 있는 종목이 없으면 표마다 안내 한 줄을 보인다', async () => {
    const user = userEvent.setup()
    vi.mocked(portfolioApi.getProfitDetail).mockResolvedValue({
      data: { ...DETAIL, realizedKrw: 0, unrealizedKrw: 0, sales: [], holdings: [] },
    } as never)
    renderTab()

    await user.click(await screen.findByRole('button', { name: /자세히 펼치기/ }))

    expect(rows('판 거래')).toEqual([
      ['날짜', '계좌', '종목', '수량', '판 가격 $', '실현 손익 $', '실현 손익 ₩', '수익률'],
      ['이 기간에 판 거래가 없습니다'],
    ])
    expect(rows('들고 있는 종목')).toEqual([
      ['종목', '수량', '평균 단가 $', '종가 $', '평가 손익 $', '평가 손익 ₩', '수익률'],
      ['들고 있는 종목이 없습니다'],
    ])
  })

  it('계좌 하나면 판 거래 표에 계좌 칸이 없다', async () => {
    const user = userEvent.setup()
    renderTab(3)

    await user.click(await screen.findByRole('button', { name: /자세히 펼치기/ }))

    expect(portfolioApi.getProfitDetail).toHaveBeenCalledWith(3, 'ALL')
    expect(rows('판 거래')[0]).toEqual(['날짜', '종목', '수량', '판 가격 $', '실현 손익 $', '실현 손익 ₩', '수익률'])
  })

  it('이 기간에 거래가 없으면 막대와 펼침 대신 안내 한 줄을 보인다', async () => {
    vi.mocked(portfolioApi.getProfitDetail).mockResolvedValue({ data: EMPTY } as never)
    renderTab()

    expect(await screen.findByText('이 기간에 거래가 없습니다')).toBeInTheDocument()
    expect(screen.queryByTestId('bars')).toBeNull()
    expect(screen.queryByRole('button', { name: /자세히 펼치기/ })).toBeNull()
  })

  it('수익 내역을 못 받으면 오류와 다시 시도 단추를 보인다', async () => {
    const user = userEvent.setup()
    vi.mocked(portfolioApi.getProfitDetail).mockRejectedValueOnce(new Error('503'))
    renderTab()

    expect(await screen.findByText('수익 내역을 불러오지 못했습니다')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '다시 시도' }))

    expect(await screen.findByTestId('bars')).toBeInTheDocument()
    expect(portfolioApi.getProfitDetail).toHaveBeenCalledTimes(2)
  })
})
