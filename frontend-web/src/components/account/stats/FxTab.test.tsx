import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { portfolioApi, type FxStatsResponse, type ProfitFlowPeriod } from '../../../services/api'
import { FxTab } from './FxTab'

vi.mock('../../../services/api', () => ({ portfolioApi: { getFxStats: vi.fn() } }))

// 환전은 최근부터, 효과는 환전 달러(판 달러는 음수) × (1,385.20 − 환전 환율)
const FX: FxStatsResponse = {
  fxEffectKrw: 261_637,
  currentRate: 1385.2,
  averageRate: 1298.01,
  heldUsd: 3000.75,
  krwToUsdKrw: 4_300_000,
  usdToKrwUsd: 300,
  exchangeCount: 4,
  exchanges: [
    {
      date: '2024-10-02', accountId: 3, direction: 'USD_TO_KRW', krwAmount: 405_000, usdAmount: 300,
      rate: 1350, effectKrw: -10_560,
    },
    {
      date: '2024-09-02', accountId: 5, direction: 'KRW_TO_USD', krwAmount: 1_700_000, usdAmount: 1300.75,
      rate: 1306.94, effectKrw: 101_797,
    },
    {
      date: '2024-09-02', accountId: 3, direction: 'KRW_TO_USD', krwAmount: 1_300_000, usdAmount: 1000,
      rate: 1300, effectKrw: 85_200,
    },
    {
      date: '2024-09-02', accountId: 3, direction: 'KRW_TO_USD', krwAmount: 1_300_000, usdAmount: 1000,
      rate: 1300, effectKrw: 85_200,
    },
  ],
}

const EMPTY: FxStatsResponse = {
  fxEffectKrw: 0, currentRate: 1385.2, averageRate: null, heldUsd: 0,
  krwToUsdKrw: 0, usdToKrwUsd: 0, exchangeCount: 0, exchanges: [],
}

const NAMES = { 3: '연습', 5: '배당' }

function renderTab(period: ProfitFlowPeriod = 'ALL', accountId: number | 'all' = 'all') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <FxTab accountId={accountId} period={period} accountNames={NAMES} />
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
  vi.mocked(portfolioApi.getFxStats).mockResolvedValue({ data: FX } as never)
})

describe('FxTab', () => {
  it('계좌 · 기간 환율 효과를 불러 위 카드에 기간 환율 효과와 지금 환율 · 평균 환전 환율을 보인다', async () => {
    renderTab()

    await screen.findByRole('button', { name: /자세히 펼치기/ })
    expect(portfolioApi.getFxStats).toHaveBeenCalledWith('all', 'ALL')
    expect(stat('환율 효과')).toHaveTextContent('환율 효과+₩261,637전체 기간')
    expect(stat('지금 환율')).toHaveTextContent('지금 환율₩1,385.20평균 환전 환율 ₩1,298.01')
  })

  it('환율 효과 아래 줄은 고른 기간이다', async () => {
    renderTab('1M')

    await screen.findByRole('button', { name: /자세히 펼치기/ })
    expect(portfolioApi.getFxStats).toHaveBeenCalledWith('all', '1M')
    expect(stat('환율 효과')).toHaveTextContent('환율 효과+₩261,637최근 1달')
  })

  it('아래 카드는 환전한 달러 원금과 기간 안 환전 횟수 · 원화→달러 원화 합 · 달러→원화 달러 합이고 그 아래 한 줄 설명을 둔다', async () => {
    renderTab()

    await screen.findByRole('button', { name: /자세히 펼치기/ })
    expect(stat('환전한 달러 원금')).toHaveTextContent('환전한 달러 원금$3,000.75받은 달러 − 판 달러')
    expect(stat('기간 안 환전')).toHaveTextContent('기간 안 환전4회원화→달러 ₩4,300,000달러→원화 $300.00')
    expect(screen.getByText('환전한 때와 지금의 환율 차이 · 수익 흐름에는 들어가지 않음')).toBeInTheDocument()
  })

  it('자세히 펼치기는 닫힌 채 기간 안 환전 횟수를 보이고, 열면 환전 기록 표를 최근부터 보인다', async () => {
    const user = userEvent.setup()
    renderTab()

    const fold = await screen.findByRole('button', { name: /자세히 펼치기/ })
    expect(fold).toHaveAttribute('aria-expanded', 'false')
    expect(fold).toHaveTextContent('자세히 펼치기환전 4회')
    await user.click(fold)

    expect(rows('환전 기록')).toEqual([
      ['날짜', '계좌', '방향', '원화', '달러', '환율', '지금 기준 효과 ₩'],
      ['2024-10-02', '연습', '달러→원화', '₩405,000', '$300.00', '₩1,350.00', '-₩10,560'],
      ['2024-09-02', '배당', '원화→달러', '₩1,700,000', '$1,300.75', '₩1,306.94', '+₩101,797'],
      ['2024-09-02', '연습', '원화→달러', '₩1,300,000', '$1,000.00', '₩1,300.00', '+₩85,200'],
      ['2024-09-02', '연습', '원화→달러', '₩1,300,000', '$1,000.00', '₩1,300.00', '+₩85,200'],
    ])
  })

  it('계좌 하나면 환전 기록 표에 계좌 칸이 없다', async () => {
    const user = userEvent.setup()
    renderTab('ALL', 3)

    await user.click(await screen.findByRole('button', { name: /자세히 펼치기/ }))

    expect(portfolioApi.getFxStats).toHaveBeenCalledWith(3, 'ALL')
    expect(rows('환전 기록')[0]).toEqual(['날짜', '방향', '원화', '달러', '환율', '지금 기준 효과 ₩'])
  })

  it('목록에 없는 계좌는 계좌 칸에 계좌 ID 를 보인다', async () => {
    const user = userEvent.setup()
    vi.mocked(portfolioApi.getFxStats).mockResolvedValue({
      data: { ...FX, exchanges: [{ ...FX.exchanges[0], accountId: 9 }] },
    } as never)
    renderTab()

    await user.click(await screen.findByRole('button', { name: /자세히 펼치기/ }))

    expect(rows('환전 기록')[1].slice(0, 2)).toEqual(['2024-10-02', '계좌 9'])
  })

  it('기간을 바꿔 다시 불러오는 동안에도 열어 둔 자세히 펼치기는 열린 채 새 기록으로 바뀐다', async () => {
    const user = userEvent.setup()
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const tab = (period: ProfitFlowPeriod) => (
      <QueryClientProvider client={client}>
        <FxTab accountId="all" period={period} accountNames={NAMES} />
      </QueryClientProvider>
    )
    const { rerender } = render(tab('ALL'))
    await user.click(await screen.findByRole('button', { name: /자세히 펼치기/ }))
    let resolve: (value: unknown) => void = () => {}
    vi.mocked(portfolioApi.getFxStats).mockReturnValueOnce(new Promise((done) => (resolve = done)) as never)

    rerender(tab('1M'))

    expect(portfolioApi.getFxStats).toHaveBeenLastCalledWith('all', '1M')
    expect(screen.getByRole('button', { name: /자세히 펼치기/ })).toHaveAttribute('aria-expanded', 'true')
    resolve({ data: { ...FX, exchanges: FX.exchanges.slice(0, 1) } })
    await waitFor(() => expect(rows('환전 기록')).toHaveLength(2))
    expect(screen.getByRole('button', { name: /자세히 펼치기/ })).toHaveAttribute('aria-expanded', 'true')
  })

  it('지금 환율이 없으면 환율 효과 · 지금 환율 · 표의 효과 칸은 - 이고 평균 환전 환율이 없으면 그 값도 - 이다', async () => {
    const user = userEvent.setup()
    vi.mocked(portfolioApi.getFxStats).mockResolvedValue({
      data: {
        ...FX,
        fxEffectKrw: 0,
        currentRate: null,
        averageRate: null,
        exchanges: FX.exchanges.slice(0, 1).map((exchange) => ({ ...exchange, effectKrw: 0 })),
      },
    } as never)
    renderTab()

    await user.click(await screen.findByRole('button', { name: /자세히 펼치기/ }))

    expect(stat('환율 효과')).toHaveTextContent(/^환율 효과-$/)
    expect(stat('지금 환율')).toHaveTextContent('지금 환율-평균 환전 환율 -')
    expect(rows('환전 기록')[1]).toEqual(['2024-10-02', '연습', '달러→원화', '₩405,000', '$300.00', '₩1,350.00', '-'])
  })

  it('기간 안 환전이 없어도 앞서 환전한 달러가 있으면 카드를 보이고 표 자리에 안내 한 줄을 보인다', async () => {
    const user = userEvent.setup()
    vi.mocked(portfolioApi.getFxStats).mockResolvedValue({
      data: { ...EMPTY, fxEffectKrw: 45_000, averageRate: 1300, heldUsd: 1000 },
    } as never)
    renderTab('1M')

    await user.click(await screen.findByRole('button', { name: /자세히 펼치기/ }))

    expect(stat('환율 효과')).toHaveTextContent('환율 효과+₩45,000최근 1달')
    expect(stat('기간 안 환전')).toHaveTextContent('기간 안 환전0회원화→달러 ₩0달러→원화 $0.00')
    expect(rows('환전 기록')).toEqual([
      ['날짜', '계좌', '방향', '원화', '달러', '환율', '지금 기준 효과 ₩'],
      ['이 기간에 환전 기록이 없습니다'],
    ])
  })

  it('환전 기록이 없고 효과 · 달러 원금도 0 이면 카드 대신 안내 한 줄을 보인다', async () => {
    vi.mocked(portfolioApi.getFxStats).mockResolvedValue({ data: EMPTY } as never)
    renderTab()

    expect(await screen.findByText('이 기간에 환전 기록이 없습니다')).toBeInTheDocument()
    expect(document.querySelector('[data-stat]')).toBeNull()
    expect(screen.queryByRole('button', { name: /자세히 펼치기/ })).toBeNull()
  })

  it('환율 효과를 못 받으면 오류와 다시 시도 단추를 보인다', async () => {
    const user = userEvent.setup()
    vi.mocked(portfolioApi.getFxStats).mockRejectedValueOnce(new Error('502'))
    renderTab()

    expect(await screen.findByText('환율 효과를 불러오지 못했습니다')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '다시 시도' }))

    expect(await screen.findByRole('button', { name: /자세히 펼치기/ })).toBeInTheDocument()
    expect(portfolioApi.getFxStats).toHaveBeenCalledTimes(2)
  })
})
