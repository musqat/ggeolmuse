import { describe, it, expect, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SymbolComparisonResult } from './SymbolComparisonResult'
import { CompareSymbolsChartMemoized } from './CompareSymbolsChartMemoized'
import { readCharts } from '../../../test/readCharts'

vi.mock('recharts', () => import('../../../test/rechartsProbe'))
vi.mock('./CompareSymbolsChartMemoized', () => ({ CompareSymbolsChartMemoized: vi.fn(() => null) }))

const metrics = (cagr: number, mdd: number) => ({
  cagr,
  mdd,
  mddPeakDate: '2023-07-31',
  mddTroughDate: '2023-10-27',
  mddRecoveryDate: null,
  volatility: 20,
  sharpe: 0.5,
})

// 서버 모양: 표에 쓰는 칸은 위, 상세 · 일별 값 · 지표는 additionalData
const item = (symbol: string, totalReturnPercent: number, cagr: number, mdd: number, endValue: number) => ({
  code: symbol,
  name: symbol,
  totalReturnPercent,
  totalReturnKrw: endValue - 1000000,
  currentValueKrw: endValue - 10000,
  fxReturnPercent: 3.85,
  optimalBuyDate: '2023-03-01',
  optimalBuyPrice: 95,
  optimalSellDate: '2023-12-01',
  optimalSellPrice: 130,
  additionalData: {
    purchaseDate: '2023-01-03',
    currentDate: '2024-01-03',
    investmentAmount: 1000000,
    totalAssetKrw: endValue,
    shares: 10,
    purchasePrice: 100,
    currentPrice: 110,
    purchaseFxRate: 1300,
    currentFxRate: 1350,
    optimalReturnPercent: 36.84,
    metricsKrw: metrics(cagr, mdd),
    metricsUsd: metrics(cagr - 2, mdd - 3),
    dailyValues: [
      { date: '2023-01-03', valueUsd: 769.23, valueKrw: 1000000 },
      { date: '2024-01-03', valueUsd: endValue / 1350, valueKrw: endValue },
    ],
  },
})

const result = {
  mode: 'compare-symbols',
  investmentAmount: 1000000,
  averageReturn: 10,
  medianReturn: 10,
  bestPerformer: { name: 'MSFT', code: 'MSFT', totalReturnPercent: 15 },
  items: [item('AAPL', 5, 5.1, -30, 1050000), item('MSFT', 15, 15.2, -12, 1150000)],
}

const renderResult = (value: object = result, purchaseDate = '2023-01-03') =>
  render(<SymbolComparisonResult result={value as never} comparePurchaseDate={purchaseDate} compareSaleDate="" />)

const row = (symbol: string) => screen.getByText(symbol, { selector: 'td span span' }).closest('tr') as HTMLElement

describe('SymbolComparisonResult', () => {
  it('요약 한 줄에 종목 수 · 투자금 · 최고 · 평균 · 중앙값을 보인다', () => {
    renderResult()

    expect(screen.getByText(/종목 2개 · ₩1,000,000씩/)).toHaveTextContent(
      '종목 2개 · ₩1,000,000씩 · 최고 MSFT +15.00% · 평균 +10.00% · 중앙값 +10.00%',
    )
  })

  it('표는 총 수익률 순이고 종목 코드를 따로 보인다', () => {
    renderResult()

    const names = screen.getAllByText(/^(AAPL|MSFT)$/, { selector: 'td span span' }).map((el) => el.textContent)
    expect(names).toEqual(['MSFT', 'AAPL'])
    expect(screen.getByText('MSFT', { exact: true, selector: 'td span span' })).toBeVisible()
  })

  it('칸마다 가장 좋은 값에 최고, 최대 낙폭은 가장 적음을 표시한다', () => {
    renderResult()

    expect(within(row('MSFT')).getAllByText('최고')).toHaveLength(3)
    expect(within(row('MSFT')).getByText('가장 적음')).toBeInTheDocument()
    expect(within(row('AAPL')).queryByText('최고')).toBeNull()
    expect(within(row('MSFT')).getByText('23.07.31 → 23.10.27 · 회복 전')).toBeInTheDocument()
  })

  it('줄을 누르면 그 종목 상세가 펼쳐지고 다른 줄을 누르면 바뀐다', async () => {
    renderResult()

    await userEvent.click(row('AAPL'))
    expect(screen.getByText('보유일').nextElementSibling).toHaveTextContent('365일')
    expect(screen.getByText('보유 주식').nextElementSibling).toHaveTextContent('10.00주')
    expect(screen.getByText('주식 · 현금').nextElementSibling).toHaveTextContent('₩1,040,000 · ₩10,000')
    expect(screen.getByText('매수 환율').nextElementSibling).toHaveTextContent('₩1,300.00')
    expect(screen.getByText('최적 매수').nextElementSibling).toHaveTextContent('2023-03-01 · $95.00')
    expect(screen.getByRole('button', { name: 'AAPL 상세' })).toHaveAttribute('aria-expanded', 'true')

    await userEvent.click(row('MSFT'))
    expect(screen.getAllByText('보유일')).toHaveLength(1)
    expect(screen.getByRole('button', { name: 'AAPL 상세' })).toHaveAttribute('aria-expanded', 'false')
  })

  it('달러 기준이면 연평균 · 최대 낙폭만 달러로 바꾸고 총 수익률은 그대로 둔다', async () => {
    renderResult()

    await userEvent.click(screen.getByRole('button', { name: '달러 기준' }))

    expect(row('MSFT')).toHaveTextContent('+15.00%')
    expect(row('MSFT')).toHaveTextContent('+13.20%')
    expect(row('MSFT')).toHaveTextContent('-15.00%')
    expect(screen.getByText(/총 수익률 · 평가 가치는 원화 기준/)).toBeInTheDocument()
  })

  it('수익률 추이는 종목마다 일별 값 ÷ 투자금을 배수로 그린다', () => {
    renderResult()

    const [chart] = readCharts()
    expect(chart.lines).toEqual(['MSFT:MSFT', 'AAPL:AAPL'])
    expect(chart.points).toEqual([
      { date: '2023-01-03', MSFT: 1, AAPL: 1 },
      { date: '2024-01-03', MSFT: 1.15, AAPL: 1.05 },
    ])
  })

  it('주가 추이 비교는 접어 두고 열 때 그린다', async () => {
    renderResult()

    expect(vi.mocked(CompareSymbolsChartMemoized)).not.toHaveBeenCalled()
    await userEvent.click(screen.getByText('주가 추이 비교'))
    expect(vi.mocked(CompareSymbolsChartMemoized)).toHaveBeenCalled()
  })

  it('고른 시작일이 휴장일이라 다른 날 샀으면 한 번 알린다', () => {
    renderResult(result, '2023-01-01')

    expect(screen.getAllByText(/휴장일이라/)).toHaveLength(1)
  })

  it('일별 값이 없는 응답이면 수익률 추이 자리에 안내를 보인다', () => {
    const bare = { ...result, items: result.items.map((i) => ({ ...i, additionalData: { ...i.additionalData, dailyValues: [] } })) }
    renderResult(bare)

    expect(screen.getByText('이 결과에는 일별 값이 없습니다')).toBeInTheDocument()
  })
})
