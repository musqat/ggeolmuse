import { describe, it, expect, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StrategyComparisonResult } from './StrategyComparisonResult'
import { CompareStrategiesChart } from '@components/charts/backtest/CompareStrategiesChart'
import { readCharts } from '../../../test/readCharts'

vi.mock('recharts', () => import('../../../test/rechartsProbe'))
vi.mock('@components/charts/backtest/CompareStrategiesChart', () => ({ CompareStrategiesChart: vi.fn(() => null) }))

const metrics = (cagr: number, mdd: number) => ({ cagr, mdd, volatility: 20, sharpe: 0.5 })

const result = {
  mode: 'compare-strategies',
  investmentAmount: 1000000,
  bestPerformer: { name: 'DCA', code: 'AAPL', totalReturnPercent: 20 },
  items: [
    {
      name: 'SIMPLE',
      code: 'AAPL',
      totalInvested: 1000000,
      currentValueKrw: 1090000,
      totalReturnPercent: 10,
      totalReturnKrw: 100000,
      averagePrice: 100,
      totalShares: 7.69,
      additionalData: {
        symbol: 'AAPL',
        purchaseDate: '2024-01-02',
        investmentAmount: 1000000,
        purchaseFxRate: 1300,
        currentFxRate: 1350,
        totalAssetKrw: 1100000,
        metricsKrw: metrics(9.8, -25),
        metricsUsd: metrics(8, -27),
        dailyValues: [
          { date: '2024-01-02', valueUsd: 769.23, valueKrw: 1000000 },
          { date: '2024-02-01', valueUsd: 800, valueKrw: 1100000 },
        ],
      },
    },
    {
      name: 'DCA',
      code: 'AAPL',
      totalInvested: 200000,
      currentValueKrw: 240000,
      totalReturnPercent: 20,
      totalReturnKrw: 40000,
      averagePrice: 98,
      totalShares: 1.6,
      additionalData: {
        symbol: 'AAPL',
        averageFxRate: 1310,
        currentFxRate: 1350,
        metricsKrw: metrics(19, -10),
        metricsUsd: metrics(17, -12),
        transactions: [
          { date: '2024-01-02', price: 100, shares: 0.8, amount: 100000, fxRate: 1300 },
          { date: '2024-01-20', price: 101, shares: 0.01, amount: 0, fxRate: 1310, trigger: '배당 재투자' },
          { date: '2024-02-01', price: 96, shares: 0.8, amount: 100000, fxRate: 1320 },
        ],
        dailyValues: [
          { date: '2024-01-02', valueUsd: 76.92, valueKrw: 100000 },
          { date: '2024-02-01', valueUsd: 180, valueKrw: 240000 },
        ],
      },
    },
  ],
}

const renderResult = (value: object = result) => render(<StrategyComparisonResult result={value as never} />)
const row = (name: string) => screen.getByText(name, { selector: 'td span span' }).closest('tr') as HTMLElement

describe('StrategyComparisonResult', () => {
  it('요약 한 줄에 종목 · 예산 · 전략 수 · 최고 전략을 한글 이름으로 보인다', () => {
    renderResult()

    expect(screen.getByText(/예산 ₩1,000,000/)).toHaveTextContent('AAPL · 예산 ₩1,000,000 · 전략 2개 · 최고 적립식 +20.00%')
  })

  it('표는 총 수익률 순이고 투자금 칸을 따로 두며 투자금 · 평가 가치에는 최고 표시가 없다', () => {
    renderResult()

    const names = screen.getAllByText(/^(단순 매수|적립식)$/, { selector: 'td span span' }).map((el) => el.textContent)
    expect(names).toEqual(['적립식', '단순 매수'])
    expect(row('적립식')).toHaveTextContent('₩200,000')
    expect(within(row('적립식')).getAllByText('최고')).toHaveLength(2)
    expect(within(row('적립식')).getByText('가장 적음')).toBeInTheDocument()
    expect(row('단순 매수')).toHaveTextContent('₩1,100,000')
    expect(within(row('단순 매수')).queryByText('최고')).toBeNull()
  })

  it('줄을 누르면 매수 · 위험 · 환율이 펼쳐지고 매수 횟수에서 배당 재투자를 뺀다', async () => {
    renderResult()

    await userEvent.click(row('적립식'))
    expect(screen.getByText('매수 횟수').nextElementSibling).toHaveTextContent('2회')
    expect(screen.getByText('평균 환율').nextElementSibling).toHaveTextContent('₩1,310.00')
    expect(screen.getByText('입금을 뺀 시간가중 수익률로 셈')).toBeInTheDocument()

    await userEvent.click(row('단순 매수'))
    expect(screen.getByText('매수 횟수').nextElementSibling).toHaveTextContent('1회')
    expect(screen.getByText('매수가').nextElementSibling).toHaveTextContent('$100.00')
    expect(screen.getByText('매수 환율').nextElementSibling).toHaveTextContent('₩1,300.00')
  })

  it('매수가 · 보유 주식이 비면 단위 없이 - 로 보인다', async () => {
    renderResult({ ...result, items: [{ ...result.items[1], averagePrice: undefined, totalShares: undefined }] })

    await userEvent.click(row('적립식'))
    expect(screen.getByText('평균 매수가').nextElementSibling).toHaveTextContent(/^-$/)
    expect(screen.getByText('보유 주식').nextElementSibling).toHaveTextContent(/^-$/)
  })

  it('수익률 추이는 그때까지 넣은 돈 대비 % 이고 적립식 매수일에 점을 찍는다', () => {
    renderResult()

    const [chart] = readCharts()
    const rounded = chart.points.map((p: { date: string; DCA: number; SIMPLE: number }) => [p.date, Math.round(p.DCA), Math.round(p.SIMPLE)])
    expect(rounded).toEqual([
      ['2024-01-02', 0, 0],
      ['2024-02-01', 20, 10],
    ])
    expect(chart.dots.map((d: { x: string }) => d.x)).toEqual(['2024-01-02', '2024-02-01'])
  })

  it('주가 추이 비교는 접어 두고 열 때 그린다', async () => {
    renderResult()

    expect(vi.mocked(CompareStrategiesChart)).not.toHaveBeenCalled()
    await userEvent.click(screen.getByText('주가 추이 비교'))
    expect(vi.mocked(CompareStrategiesChart)).toHaveBeenCalled()
  })

  it('수익이 비어 있으면 회색 - 로 보여준다', () => {
    renderResult({ ...result, items: [{ ...result.items[0], totalReturnPercent: undefined }] })

    expect(row('단순 매수')).toHaveTextContent('-')
  })
})
