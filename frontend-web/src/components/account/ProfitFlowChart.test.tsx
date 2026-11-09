import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'
import { ProfitFlowChart } from './ProfitFlowChart'
import { readCharts, readTooltips, readXAxes } from '../../test/readCharts'
import type { ProfitFlowResponse } from '../../services/api'

vi.mock('recharts', () => import('../../test/rechartsProbe'))

const FLOW: ProfitFlowResponse = {
  points: [
    { date: '2026-07-01', profitKrw: 100_000, investedKrw: 1_000_000 },
    { date: '2026-07-02', profitKrw: 400_000, investedKrw: 1_000_000 },
    { date: '2026-07-03', profitKrw: -50_000, investedKrw: 1_000_000 },
    { date: '2026-07-06', profitKrw: 250_000, investedKrw: 1_000_000 },
  ],
  best: { date: '2026-07-02', profitKrw: 400_000, investedKrw: 1_000_000 },
  worst: { date: '2026-07-03', profitKrw: -50_000, investedKrw: 1_000_000 },
  currentProfitKrw: 250_000,
}

describe('ProfitFlowChart', () => {
  it('거래일마다 원화 수익을 선 하나로 그리고 0 에 기준선을 둔다', () => {
    render(<ProfitFlowChart flow={FLOW} />)

    const [chart] = readCharts()
    expect(chart.points.map((p: { date: string; profitKrw: number }) => [p.date, p.profitKrw])).toEqual([
      ['2026-07-01', 100_000],
      ['2026-07-02', 400_000],
      ['2026-07-03', -50_000],
      ['2026-07-06', 250_000],
    ])
    expect(chart.lines).toEqual(['profitKrw:수익'])
    expect(chart.refLines).toEqual([{ y: 0 }])
    expect(readXAxes()[0].ticks).toBeNull()
    expect(readTooltips()[0]).toEqual(['+₩1,234,568', '수익'])
  })

  it('최고 · 최저 날에 수익 색 · 손실 색 점을 찍는다', () => {
    render(<ProfitFlowChart flow={FLOW} />)

    expect(readCharts()[0].dots).toEqual([
      { x: '2026-07-02', y: 400_000, fill: 'rgb(var(--gain))' },
      { x: '2026-07-03', y: -50_000, fill: 'rgb(var(--loss))' },
    ])
  })

  it('1년이 넘으면 달마다 첫 거래일에 눈금을 둔다', () => {
    const points = [
      { date: '2025-01-02', profitKrw: 0, investedKrw: 1_000_000 },
      { date: '2025-02-03', profitKrw: 10_000, investedKrw: 1_000_000 },
      { date: '2026-03-02', profitKrw: 20_000, investedKrw: 1_000_000 },
    ]
    render(<ProfitFlowChart flow={{ ...FLOW, points, best: points[2], worst: points[0] }} />)

    expect(readXAxes()[0]).toEqual({ ticks: ['2025-01-02', '2025-02-03', '2026-03-02'], first: '25.1' })
  })
})
