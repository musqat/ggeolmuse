import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { portfolioApi, type SymbolContribution, type SymbolContributionResponse } from '../../../services/api'
import { SymbolsTab } from './SymbolsTab'

vi.mock('../../../services/api', () => ({ portfolioApi: { getSymbolContributions: vi.fn() } }))
vi.mock('./StatsBarChart', () => ({
  StatsBarChart: ({ data }: { data: unknown }) => <i data-testid="bars" data-bars={JSON.stringify(data)} />,
}))

const contribution = (
  symbol: string,
  totalKrw: number,
  sharePercent: number,
  held = false,
  parts: Partial<SymbolContribution> = {}
): SymbolContribution => ({
  symbol, realizedKrw: totalKrw, unrealizedKrw: 0, dividendKrw: 0, feeKrw: 0, totalKrw, sharePercent, held, ...parts,
})

// 합 큰 것부터(모든 종목 합계 절댓값 합 378,114)
const SYMBOLS: SymbolContributionResponse = {
  totalKrw: 334_514,
  symbols: [
    contribution('AAPL', 236_314, 62.5, true, { realizedKrw: 46_834, unrealizedKrw: 190_680, feeKrw: 1_200 }),
    contribution('MSFT', 100_000, 26.45, true, { realizedKrw: 0, unrealizedKrw: 100_500, feeKrw: 500 }),
    contribution('KO', 10_000, 2.64, false, { realizedKrw: 4_000, dividendKrw: 6_500, feeKrw: 500 }),
    contribution('PEP', 5_000, 1.32),
    contribution('JNJ', 3_000, 0.79),
    contribution('PG', 2_000, 0.53),
    contribution('T', 0, 0, true, { realizedKrw: 0 }),
    contribution('VZ', -1_800, -0.48),
    contribution('TSLA', -20_000, -5.29),
  ],
}

function renderTab() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <SymbolsTab accountId="all" period="ALL" />
    </QueryClientProvider>
  )
}

const stat = (label: string) => document.querySelector(`[data-stat="${label}"]`)

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(portfolioApi.getSymbolContributions).mockResolvedValue({ data: SYMBOLS } as never)
})

describe('SymbolsTab', () => {
  it('계좌 · 기간 종목별 기여를 불러 위 카드에 기간 수익과 종목 수를 보인다', async () => {
    renderTab()

    await screen.findByTestId('bars')
    expect(portfolioApi.getSymbolContributions).toHaveBeenCalledWith('all', 'ALL')
    expect(stat('기간 수익')).toHaveTextContent('기간 수익+₩334,514실현 + 평가 + 배당 − 수수료')
    expect(stat('종목 수')).toHaveTextContent('종목 수거래한 9개들고 있는 3개')
  })

  it('가장 많이 번 · 잃은 종목은 종목 이름 · 합 · 표와 같은 부호를 둔 비율이다', async () => {
    renderTab()

    await screen.findByTestId('bars')
    expect(stat('가장 많이 번 종목')).toHaveTextContent(/^가장 많이 번 종목AAPL\+₩236,314\(\+62\.50%\)$/)
    expect(stat('가장 많이 잃은 종목')).toHaveTextContent(/^가장 많이 잃은 종목TSLA-₩20,000\(-5\.29%\)$/)
  })

  it('번 종목이나 잃은 종목이 없으면 그 칸은 - 이다', async () => {
    vi.mocked(portfolioApi.getSymbolContributions).mockResolvedValue({
      data: { totalKrw: 0, symbols: [contribution('T', 0, 0, true)] },
    } as never)
    renderTab()

    await screen.findByTestId('bars')
    expect(stat('가장 많이 번 종목')).toHaveTextContent(/^가장 많이 번 종목-$/)
    expect(stat('가장 많이 잃은 종목')).toHaveTextContent(/^가장 많이 잃은 종목-$/)
  })

  it('종목별 막대는 목록 위 8개 종목의 비율이다', async () => {
    renderTab()

    const bars = await screen.findByTestId('bars')
    expect(screen.getByRole('heading', { name: '종목별 비율' })).toBeInTheDocument()
    expect(JSON.parse(bars.dataset.bars ?? '[]')).toEqual([
      { label: 'AAPL', value: 62.5 },
      { label: 'MSFT', value: 26.45 },
      { label: 'KO', value: 2.64 },
      { label: 'PEP', value: 1.32 },
      { label: 'JNJ', value: 0.79 },
      { label: 'PG', value: 0.53 },
      { label: 'T', value: 0 },
      { label: 'VZ', value: -0.48 },
    ])
  })

  it('자세히 펼치기는 닫힌 채 종목 수를 보이고, 열면 모든 종목 표를 보이며 들고 있는 종목은 이름 옆에 보유를 단다', async () => {
    const user = userEvent.setup()
    renderTab()

    const fold = await screen.findByRole('button', { name: /자세히 펼치기/ })
    expect(fold).toHaveAttribute('aria-expanded', 'false')
    expect(fold).toHaveTextContent('자세히 펼치기종목 9개')
    await user.click(fold)

    const table = within(screen.getByRole('table', { name: '종목별 기여' }))
    const cells = table.getAllByRole('row').map((row) => [...row.querySelectorAll('th, td')].map((cell) => cell.textContent))
    expect(cells).toHaveLength(10)
    expect(cells.slice(0, 4)).toEqual([
      ['종목', '실현', '평가', '배당', '수수료', '합', '비율'],
      ['AAPL보유', '+₩46,834', '+₩190,680', '₩0', '₩1,200', '+₩236,314', '+62.50%'],
      ['MSFT보유', '₩0', '+₩100,500', '₩0', '₩500', '+₩100,000', '+26.45%'],
      ['KO', '+₩4,000', '₩0', '₩6,500', '₩500', '+₩10,000', '+2.64%'],
    ])
    expect(cells[9]).toEqual(['TSLA', '-₩20,000', '₩0', '₩0', '₩0', '-₩20,000', '-5.29%'])
  })

  it('계좌를 바꿔 다시 불러오는 동안에도 열어 둔 자세히 펼치기는 열린 채 새 표로 바뀐다', async () => {
    const user = userEvent.setup()
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const tab = (accountId: number | 'all') => (
      <QueryClientProvider client={client}>
        <SymbolsTab accountId={accountId} period="ALL" />
      </QueryClientProvider>
    )
    const { rerender } = render(tab('all'))
    await user.click(await screen.findByRole('button', { name: /자세히 펼치기/ }))
    let resolve: (value: unknown) => void = () => {}
    vi.mocked(portfolioApi.getSymbolContributions).mockReturnValueOnce(
      new Promise((done) => (resolve = done)) as never
    )

    rerender(tab(3))

    expect(portfolioApi.getSymbolContributions).toHaveBeenLastCalledWith(3, 'ALL')
    expect(screen.getByRole('button', { name: /자세히 펼치기/ })).toHaveAttribute('aria-expanded', 'true')
    resolve({ data: { totalKrw: 236_314, symbols: [SYMBOLS.symbols[0]] } })
    await waitFor(() => expect(screen.getAllByRole('row')).toHaveLength(2))
    expect(screen.getByRole('button', { name: /자세히 펼치기/ })).toHaveAttribute('aria-expanded', 'true')
  })

  it('이 기간에 종목이 없으면 카드 대신 안내 한 줄을 보인다', async () => {
    vi.mocked(portfolioApi.getSymbolContributions).mockResolvedValue({ data: { totalKrw: 0, symbols: [] } } as never)
    renderTab()

    expect(await screen.findByText('이 기간에 거래한 종목이 없습니다')).toBeInTheDocument()
    expect(document.querySelector('[data-stat]')).toBeNull()
    expect(screen.queryByTestId('bars')).toBeNull()
  })

  it('종목별 기여를 못 받으면 오류와 다시 시도 단추를 보인다', async () => {
    const user = userEvent.setup()
    vi.mocked(portfolioApi.getSymbolContributions).mockRejectedValueOnce(new Error('503'))
    renderTab()

    expect(await screen.findByText('종목별 기여를 불러오지 못했습니다')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '다시 시도' }))

    expect(await screen.findByTestId('bars')).toBeInTheDocument()
    expect(portfolioApi.getSymbolContributions).toHaveBeenCalledTimes(2)
  })
})
