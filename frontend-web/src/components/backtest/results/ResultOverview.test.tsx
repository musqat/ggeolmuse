import { describe, it, expect, vi } from 'vitest'
import { useState, type ComponentProps } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ResultOverview as Overview } from './ResultOverview'
import type { MetricCurrency } from './CurrencyToggle'
import { readCharts } from '../../../test/readCharts'

vi.mock('recharts', () => import('../../../test/rechartsProbe'))

const metricsKrw = {
  cagr: 14.86,
  mdd: -28.1,
  mddPeakDate: '2020-02-19',
  mddTroughDate: '2020-03-23',
  mddRecoveryDate: '2020-08-10',
  volatility: 24.5,
  sharpe: 0.62,
}

const metricsUsd = {
  cagr: 12.4,
  mdd: -33.5,
  mddPeakDate: '2020-02-19',
  mddTroughDate: '2020-03-23',
  mddRecoveryDate: null,
  volatility: 29.1,
  sharpe: 0.41,
}

const result = {
  totalAssetKrw: 20000000,
  currentValueKrw: 19500000,
  totalReturnKrw: 10000000,
  totalReturnPercent: 100,
  metricsKrw,
  metricsUsd,
}

const card = (label: string) => screen.getByText(label).closest('div.bg-surface') as HTMLElement

// currency 상태를 가진 ResultOverview
const ResultOverview = (props: Omit<ComponentProps<typeof Overview>, 'currency' | 'onCurrencyChange' | 'deposits'> & {
  deposits?: ComponentProps<typeof Overview>['deposits']
}) => {
  const [currency, setCurrency] = useState<MetricCurrency>('KRW')
  return <Overview deposits={[]} {...props} currency={currency} onCurrencyChange={setCurrency} />
}

describe('ResultOverview', () => {
  it('카드 한 줄에 총 수익률 · 연평균 수익률 · 최대 낙폭 · 평가 가치를 보인다', () => {
    render(<ResultOverview result={result} investedLabel="초기 투자금" invested={10000000} returnRateTestId="rate" />)

    expect(screen.getByTestId('rate')).toHaveTextContent('+100.00%')
    expect(card('총 수익률')).toHaveTextContent('초기 투자금 ₩10,000,000 대비 +₩10,000,000')
    expect(card('연평균 수익률')).toHaveTextContent('+14.86%')
    expect(card('연평균 수익률')).toHaveTextContent('달러 기준 +12.40%')
    expect(card('평가 가치')).toHaveTextContent('₩20,000,000')
    expect(card('최대 낙폭')).toBeInTheDocument()
    expect(screen.queryByText('변동성(연)')).toBeNull()
    expect(document.querySelector('svg')).toBeNull()
  })

  it('최대 낙폭 카드에 고점 · 저점 · 회복일과 다른 통화 값을 보인다', () => {
    render(<ResultOverview result={result} investedLabel="초기 투자금" invested={10000000} />)

    expect(card('최대 낙폭')).toHaveTextContent('-28.10%')
    expect(card('최대 낙폭')).toHaveTextContent('2020-02-19 → 2020-03-23 · 2020-08-10 회복')
    expect(card('최대 낙폭')).toHaveTextContent('달러 기준 -33.50%')
  })

  it('달러 기준을 누르면 지표를 달러로 바꾸고 원화 값을 작게 보인다', async () => {
    render(<ResultOverview result={result} investedLabel="초기 투자금" invested={10000000} />)

    await userEvent.click(screen.getByRole('button', { name: '달러 기준' }))

    expect(screen.getByRole('button', { name: '달러 기준' })).toHaveAttribute('aria-pressed', 'true')
    expect(card('연평균 수익률')).toHaveTextContent('+12.40%')
    expect(card('연평균 수익률')).toHaveTextContent('원화 기준 +14.86%')
    expect(card('최대 낙폭')).toHaveTextContent('-33.50%')
    expect(card('최대 낙폭')).toHaveTextContent('아직 회복 전')
  })

  it('달러 기준이면 총 수익률 카드에 원화 기준이라고 적는다', async () => {
    render(<ResultOverview result={result} investedLabel="초기 투자금" invested={10000000} />)

    expect(card('총 수익률')).not.toHaveTextContent('원화 기준')
    await userEvent.click(screen.getByRole('button', { name: '달러 기준' }))

    expect(card('총 수익률')).toHaveTextContent('원화 기준')
  })

  it('응답에 지표 칸이 없으면 지표 기준 전환을 두지 않고 지표 카드에 지표 없음을 적는다', () => {
    const noMetrics = { ...result, metricsKrw: undefined, metricsUsd: undefined }
    render(<ResultOverview result={noMetrics} investedLabel="총 투자금" invested={10000000} />)

    expect(screen.queryByRole('button', { name: '달러 기준' })).toBeNull()
    expect(card('최대 낙폭')).toHaveTextContent('—')
    expect(card('최대 낙폭')).toHaveTextContent('지표 없음')
    expect(card('연평균 수익률')).toHaveTextContent('지표 없음')
    expect(card('연평균 수익률')).not.toHaveTextContent('1년 미만')
  })

  it('지표가 없으면 빈칸 표시와 이유를 보인다', () => {
    render(
      <ResultOverview
        result={{ ...result, metricsKrw: { ...metricsKrw, cagr: null, mdd: 0, mddPeakDate: null, mddTroughDate: null, volatility: null, sharpe: null }, metricsUsd: null }}
        investedLabel="총 투자금"
        invested={10000000}
      />,
    )

    expect(card('연평균 수익률')).toHaveTextContent('—')
    expect(card('연평균 수익률')).toHaveTextContent('1년 미만이면 내지 않음')
    expect(card('최대 낙폭')).toHaveTextContent('0.00%')
    expect(card('최대 낙폭')).toHaveTextContent('기간 중 하락 없음')
  })

  it('일별 시세가 없어 지표가 비면 낙폭 이유도 그렇게 적는다', () => {
    render(<ResultOverview result={{ ...result, metricsKrw: { cagr: null, mdd: null, volatility: null, sharpe: null } }} investedLabel="초기 투자금" invested={10000000} />)

    expect(card('최대 낙폭')).toHaveTextContent('—')
    expect(card('최대 낙폭')).toHaveTextContent('일별 시세가 없어 내지 않음')
  })

  it('입금이 있는 전략이면 연평균 수익률 · 최대 낙폭이 시간가중이라고 적는다', () => {
    render(<ResultOverview result={result} investedLabel="총 투자금" invested={10000000} timeWeighted />)

    expect(card('연평균 수익률')).toHaveTextContent('입금을 뺀 시간가중 · 달러 기준 +12.40%')
    expect(card('최대 낙폭')).toHaveTextContent('입금을 뺀 시간가중 · 달러 기준 -33.50%')
  })

  it('금액과 날짜는 줄이 바뀌어도 한 덩어리로 둔다', () => {
    render(<ResultOverview result={result} investedLabel="초기 투자금" invested={10000000} />)

    expect(screen.getByText('+₩10,000,000')).toHaveClass('whitespace-nowrap')
    expect(screen.getByText('2020-03-23')).toHaveClass('whitespace-nowrap')
    expect(screen.getByText('2020-08-10 회복')).toHaveClass('whitespace-nowrap')
  })

  it('자산 추이에 고른 통화의 평가 가치 · 누적 투자금과 매수 점을 그린다', async () => {
    const dailyValues = [
      { date: '2024-01-15', valueUsd: 1000, valueKrw: 1300000 },
      { date: '2024-02-15', valueUsd: 2100, valueKrw: 2800000 },
      { date: '2024-03-15', valueUsd: 2300, valueKrw: 3000000 },
    ]
    const deposits = [
      { date: '2024-01-15', krw: 1300000, usd: 1000 },
      { date: '2024-02-15', krw: 1300000, usd: 980 },
    ]
    render(<ResultOverview result={{ ...result, dailyValues }} investedLabel="총 투자금" invested={2600000} deposits={deposits} timeWeighted />)

    const [chart] = readCharts()
    expect(chart.points.map((p: { value: number; invested: number }) => [p.value, p.invested])).toEqual([[1300000, 1300000], [2800000, 2600000], [3000000, 2600000]])
    expect(chart.lines).toEqual(['value:평가 가치', 'invested:누적 투자금'])
    expect(chart.dots).toEqual([
      { x: '2024-01-15', y: 1300000, fill: '#8b5cf6' },
      { x: '2024-02-15', y: 2600000, fill: '#8b5cf6' },
    ])

    await userEvent.click(screen.getByRole('button', { name: '달러 기준' }))
    expect(readCharts()[0].points.map((p: { value: number; invested: number }) => [p.value, p.invested])).toEqual([[1000, 1000], [2100, 1980], [2300, 1980]])
  })

  it('일별 값이 둘보다 적으면 자산 추이 자리에 안내를 보인다', () => {
    render(<ResultOverview result={result} investedLabel="초기 투자금" invested={10000000} />)

    expect(screen.getByText('이 결과에는 일별 값이 없습니다')).toBeInTheDocument()
  })
})
