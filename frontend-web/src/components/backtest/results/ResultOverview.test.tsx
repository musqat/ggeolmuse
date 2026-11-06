import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ResultOverview } from './ResultOverview'

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

describe('ResultOverview', () => {
  it('수익 줄에 총 수익률 · 연평균 수익률 · 평가 가치를 보인다', () => {
    render(<ResultOverview result={result} investedLabel="초기 투자금" invested={10000000} returnRateTestId="rate" />)

    expect(screen.getByTestId('rate')).toHaveTextContent('+100.00%')
    expect(card('총 수익률')).toHaveTextContent('초기 투자금 ₩10,000,000 대비 +₩10,000,000')
    expect(card('연평균 수익률')).toHaveTextContent('+14.86%')
    expect(card('연평균 수익률')).toHaveTextContent('달러 기준 +12.40%')
    expect(card('평가 가치')).toHaveTextContent('₩20,000,000')
  })

  it('위험 줄에 최대 낙폭의 고점 · 저점 · 회복일과 변동성 · 샤프를 보인다', () => {
    render(<ResultOverview result={result} investedLabel="초기 투자금" invested={10000000} />)

    expect(card('최대 낙폭')).toHaveTextContent('-28.10%')
    expect(card('최대 낙폭')).toHaveTextContent('2020-02-19 → 2020-03-23 · 2020-08-10 회복')
    expect(card('최대 낙폭')).toHaveTextContent('달러 기준 -33.50%')
    expect(card('변동성(연)')).toHaveTextContent('24.50%')
    expect(card('샤프 비율')).toHaveTextContent('0.62')
    expect(card('샤프 비율')).toHaveTextContent('무위험 연 3% · 달러 기준 0.41')
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

  it('응답에 지표 칸이 없으면 지표 기준 전환과 위험 줄을 두지 않는다', () => {
    const noMetrics = { ...result, metricsKrw: undefined, metricsUsd: undefined }
    render(<ResultOverview result={noMetrics} investedLabel="총 투자금" invested={10000000} />)

    expect(screen.queryByRole('button', { name: '달러 기준' })).toBeNull()
    expect(screen.queryByText('최대 낙폭')).toBeNull()
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
    expect(card('변동성(연)')).toHaveTextContent('—')
    expect(card('샤프 비율')).toHaveTextContent('—')
  })

  it('일별 시세가 없어 지표가 비면 낙폭 이유도 그렇게 적는다', () => {
    render(<ResultOverview result={{ ...result, metricsKrw: { cagr: null, mdd: null, volatility: null, sharpe: null } }} investedLabel="초기 투자금" invested={10000000} />)

    expect(card('최대 낙폭')).toHaveTextContent('—')
    expect(card('최대 낙폭')).toHaveTextContent('일별 시세가 없어 내지 않음')
  })

  it('입금이 있는 전략이면 연평균 수익률이 시간가중이라고 적는다', () => {
    render(<ResultOverview result={result} investedLabel="총 투자금" invested={10000000} timeWeighted />)

    expect(card('연평균 수익률')).toHaveTextContent('입금을 뺀 시간가중 · 달러 기준 +12.40%')
  })

  it('금액과 날짜는 줄이 바뀌어도 한 덩어리로 둔다', () => {
    render(<ResultOverview result={result} investedLabel="초기 투자금" invested={10000000} />)

    expect(screen.getByText('+₩10,000,000')).toHaveClass('whitespace-nowrap')
    expect(screen.getByText('2020-03-23')).toHaveClass('whitespace-nowrap')
    expect(screen.getByText('2020-08-10 회복')).toHaveClass('whitespace-nowrap')
  })

  it('입금이 있는 전략이면 차트에 입금을 뺀 낙폭이라고 적는다', () => {
    const dailyValues = [
      { date: '2024-01-15', valueUsd: 1000, valueKrw: 1300000, drawdownUsd: 0, drawdownKrw: 0 },
      { date: '2024-03-01', valueUsd: 1200, valueKrw: 1560000, drawdownUsd: 0, drawdownKrw: 0 },
    ]
    render(<ResultOverview result={{ ...result, dailyValues }} investedLabel="총 투자금" invested={10000000} timeWeighted />)

    expect(screen.getByText('낙폭 · 지표는 매수 입금을 뺀 수익률로 셉니다')).toBeInTheDocument()
  })
})
