import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { PurchaseDayChart } from './PurchaseDayChart'
import { MARKER_COLORS } from '@components/charts/backtest/shared/chartStyle'
import { readCharts, readTooltips } from '../../../test/readCharts'

vi.mock('recharts', () => import('../../../test/rechartsProbe'))

const returns = [
  { date: '2024-01-02', returnPercent: 25, returnPercentUsd: 20 },
  { date: '2024-01-03', returnPercent: 5, returnPercentUsd: 8 },
  { date: '2024-01-04', returnPercent: null, returnPercentUsd: 12 },
  { date: '2024-01-05', returnPercent: 25, returnPercentUsd: -3 },
  { date: '2024-01-08', returnPercent: 10, returnPercentUsd: 9 },
]

const card = (label: string) => screen.getByText(label).parentElement
const view = (currency: 'KRW' | 'USD' = 'KRW', list = returns) =>
  render(<PurchaseDayChart returns={list} currency={currency} purchaseDate="2024-01-02" valuationDate="2024-06-14" />)

describe('PurchaseDayChart', () => {
  it('매수일마다 여기서 샀다면 평가일까지 수익률을 그리고 이 결과의 매수일에 점을 찍는다', () => {
    view()

    const [chart] = readCharts()
    expect(chart.points.map((p: { date: string; returnPct: number }) => [p.date, p.returnPct])).toEqual([
      ['2024-01-02', 25],
      ['2024-01-03', 5],
      ['2024-01-05', 25],
      ['2024-01-08', 10],
    ])
    expect(chart.lines).toEqual(['returnPct:여기서 샀다면 평가일까지'])
    expect(chart.dots).toEqual([{ x: '2024-01-02', y: 25, fill: MARKER_COLORS.purchase }])
    expect(readTooltips()[0]).toEqual(['+1234567.89%', '여기서 샀다면 평가일까지'])
    expect(screen.getByText(/평가일\(2024-06-14\)까지/)).toBeInTheDocument()
  })

  it('카드에 거래일 · 목표 이상인 날 수와 비율 · 가장 좋은 매수일을 보인다', () => {
    view()

    expect(screen.getByLabelText('목표 수익률')).toHaveValue(10)
    expect(card('거래일')).toHaveTextContent('4일')
    expect(card('목표 이상인 날')).toHaveTextContent('3일전체의 75.00%')
    expect(card('가장 좋은 매수일')).toHaveTextContent('2024-01-02평가일까지 +25.00%')
  })

  it('목표선은 글자 없이 값 범위 밖이어도 보이게 축을 늘리고, 범례에 목표를 적는다', () => {
    view()

    expect(readCharts()[0].refLines).toEqual([{ y: 0 }, { y: 10, yAxisId: 0, ifOverflow: 'extendDomain' }])
    expect(screen.getByText('목표 10.00% 이상')).toBeInTheDocument()
  })

  it('목표를 바꾸면 다시 요청하지 않고 바로 다시 센다', async () => {
    view()

    await userEvent.clear(screen.getByLabelText('목표 수익률'))
    await userEvent.type(screen.getByLabelText('목표 수익률'), '20')

    expect(card('목표 이상인 날')).toHaveTextContent('2일전체의 50.00%')
  })

  it('목표 칸이 비면 목표 이상인 날은 — 이고 0% 선만 둔다', async () => {
    view()

    await userEvent.clear(screen.getByLabelText('목표 수익률'))

    expect(card('목표 이상인 날')).toHaveTextContent('—')
    expect(readCharts()[0].refLines).toEqual([{ y: 0 }])
  })

  it('달러 기준이면 달러 수익률로 그리고 센다', () => {
    view('USD')

    expect(card('거래일')).toHaveTextContent('5일')
    expect(card('목표 이상인 날')).toHaveTextContent('2일전체의 40.00%')
    expect(card('가장 좋은 매수일')).toHaveTextContent('2024-01-02평가일까지 +20.00%')
  })

  it('가장 좋은 날도 손실이면 그 수익률을 손실 색으로 보인다', () => {
    view('KRW', returns.map((r) => ({ ...r, returnPercent: r.returnPercent == null ? null : -r.returnPercent })))

    expect(screen.getByText('-5.00%')).toHaveClass('text-loss')
  })

  it('거래일이 둘보다 적으면 안내를 보인다', () => {
    view('KRW', returns.slice(0, 1))

    expect(screen.getByText('매수일별 수익률을 낼 거래일이 둘보다 적습니다')).toBeInTheDocument()
  })
})
