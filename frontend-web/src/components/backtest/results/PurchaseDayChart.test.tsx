import { describe, it, expect, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
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

describe('PurchaseDayChart', () => {
  it('매수일마다 그날 샀다면의 수익률을 그리고 결과의 매수일에 점을 찍는다', () => {
    render(<PurchaseDayChart returns={returns} currency="KRW" purchaseDate="2024-01-02" valuationDate="2024-06-14" />)

    const [chart] = readCharts()
    expect(chart.points.map((p: { date: string; returnPct: number }) => [p.date, p.returnPct])).toEqual([
      ['2024-01-02', 25],
      ['2024-01-03', 5],
      ['2024-01-05', 25],
      ['2024-01-08', 10],
    ])
    expect(chart.lines).toEqual(['returnPct:그날 샀다면'])
    expect(chart.dots).toEqual([{ x: '2024-01-02', y: 25, fill: MARKER_COLORS.purchase }])
    expect(readTooltips()[0]).toEqual(['+1234567.89%', '그날 샀다면'])
    expect(screen.getByText(/평가일\(2024-06-14\)까지/)).toBeInTheDocument()
  })

  it('목표 기본값 10% 로 목표 이상인 날 비율과 가장 좋은 매수일을 보인다', () => {
    render(<PurchaseDayChart returns={returns} currency="KRW" valuationDate="2024-06-14" />)

    expect(screen.getByLabelText('목표 수익률')).toHaveValue(10)
    expect(card('목표 이상인 날')).toHaveTextContent('75.00%거래일 4일 중 3일')
    expect(card('가장 좋은 매수일')).toHaveTextContent('+25.00%2024-01-02 매수')
  })

  it('목표를 바꾸면 화면에서 바로 다시 센다', async () => {
    render(<PurchaseDayChart returns={returns} currency="KRW" valuationDate="2024-06-14" />)

    await userEvent.clear(screen.getByLabelText('목표 수익률'))
    await userEvent.type(screen.getByLabelText('목표 수익률'), '20')

    expect(card('목표 이상인 날')).toHaveTextContent('50.00%거래일 4일 중 2일')
  })

  it('목표선은 값 범위 밖이어도 보이게 축을 늘리고, 목표 칸이 비면 0% 선만 둔다', async () => {
    render(<PurchaseDayChart returns={returns} currency="KRW" valuationDate="2024-06-14" />)

    expect(readCharts()[0].refLines).toEqual([{ y: 0 }, { y: 10, yAxisId: 0, ifOverflow: 'extendDomain' }])

    await userEvent.clear(screen.getByLabelText('목표 수익률'))
    expect(readCharts()[0].refLines).toEqual([{ y: 0 }])
  })

  it('목표 칸이 비면 비율은 — 로 둔다', async () => {
    render(<PurchaseDayChart returns={returns} currency="KRW" valuationDate="2024-06-14" />)

    await userEvent.clear(screen.getByLabelText('목표 수익률'))

    expect(card('목표 이상인 날')).toHaveTextContent('—')
    expect(card('가장 좋은 매수일')).toHaveTextContent('+25.00%2024-01-02 매수')
  })

  it('달러 기준이면 달러 수익률로 그리고 센다', () => {
    render(<PurchaseDayChart returns={returns} currency="USD" valuationDate="2024-06-14" />)

    expect(readCharts()[0].points).toHaveLength(5)
    expect(card('목표 이상인 날')).toHaveTextContent('40.00%거래일 5일 중 2일')
    expect(card('가장 좋은 매수일')).toHaveTextContent('+20.00%2024-01-02 매수')
  })

  it('매수일의 값이 없으면 점을 찍지 않는다', () => {
    render(<PurchaseDayChart returns={returns} currency="KRW" purchaseDate="2024-01-04" valuationDate="2024-06-14" />)

    expect(readCharts()[0].dots).toEqual([])
  })

  it('가장 좋은 날도 손실이면 손실 색으로 보인다', () => {
    const losses = returns.map((r) => ({ ...r, returnPercent: r.returnPercent == null ? null : -r.returnPercent }))
    render(<PurchaseDayChart returns={losses} currency="KRW" valuationDate="2024-06-14" />)

    expect(screen.getByText('-5.00%')).toHaveClass('text-loss')
  })

  it('거래일이 둘보다 적으면 안내를 보인다', () => {
    render(<PurchaseDayChart returns={returns.slice(0, 1)} currency="KRW" valuationDate="2024-06-14" />)

    expect(screen.getByText('매수일별 수익률을 낼 거래일이 둘보다 적습니다')).toBeInTheDocument()
  })
})

describe('PurchaseDayChart 매수일 비교', () => {
  const view = (currency: 'KRW' | 'USD' = 'KRW') =>
    render(<PurchaseDayChart returns={returns} currency={currency} purchaseDate="2024-01-02" valuationDate="2024-06-14" />)
  const rows = () =>
    within(screen.getByRole('table', { name: '매수일 비교' }))
      .getAllByRole('row')
      .slice(1)
      .map((row) => row.textContent)
  const point = (date: string) => userEvent.click(screen.getByRole('button', { name: `점 ${date}` }))

  it('처음에는 고른 매수일 한 줄만 둔다', () => {
    view()

    expect(rows()).toEqual(['2024-01-02고른 매수일+25.00%—'])
  })

  it('곡선의 날을 누르면 비교에 넣고 점을 찍으며, 다시 누르면 뺀다', async () => {
    view()

    await point('2024-01-08')
    await point('2024-01-03')
    expect(rows()).toEqual(['2024-01-02고른 매수일+25.00%—', '2024-01-03+5.00%-20.00%p', '2024-01-08+10.00%-15.00%p'])
    expect(readCharts()[0].dots.filter((d: { fill: string }) => d.fill === MARKER_COLORS.compare)).toEqual([
      { x: '2024-01-03', y: 5, fill: MARKER_COLORS.compare },
      { x: '2024-01-08', y: 10, fill: MARKER_COLORS.compare },
    ])

    await point('2024-01-08')
    expect(rows()).toEqual(['2024-01-02고른 매수일+25.00%—', '2024-01-03+5.00%-20.00%p'])
  })

  it('고른 매수일을 누르면 바뀌지 않는다', async () => {
    view()

    await point('2024-01-02')

    expect(rows()).toHaveLength(1)
  })

  it('빼기 단추로 그날을 뺀다', async () => {
    view()
    await point('2024-01-05')

    await userEvent.click(screen.getByRole('button', { name: '2024-01-05 비교에서 빼기' }))

    expect(rows()).toHaveLength(1)
  })

  it('날짜 칸으로 고르면 그날이나 다음 거래일을 넣는다', async () => {
    view()

    await userEvent.click(screen.getByRole('button', { name: /날짜로 넣기/ }))
    await userEvent.click(screen.getByRole('button', { name: /2024년 1월 4일/ }))

    expect(rows()).toEqual(['2024-01-02고른 매수일+25.00%—', '2024-01-05+25.00%+0.00%p'])
  })

  it('달러 기준이면 비교 값도 달러 수익률이다', async () => {
    view('USD')

    await point('2024-01-05')

    expect(rows()).toEqual(['2024-01-02고른 매수일+20.00%—', '2024-01-05-3.00%-23.00%p'])
  })
})
