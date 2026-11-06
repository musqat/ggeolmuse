import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { RiskChart } from './RiskChart'
import { riskChartData } from './riskChartData'
import { readCharts, readTooltips } from '../../../test/readCharts'

vi.mock('recharts', () => import('../../../test/rechartsProbe'))

const dailyValues = [
  { date: '2024-01-15', valueUsd: 1000, valueKrw: 1300000, drawdownUsd: 0, drawdownKrw: 0 },
  { date: '2024-03-01', valueUsd: 1200, valueKrw: 1560000, drawdownUsd: 0, drawdownKrw: 0 },
  { date: '2024-04-01', valueUsd: 600, valueKrw: 840000, drawdownUsd: -50, drawdownKrw: -46.15 },
  { date: '2024-06-14', valueUsd: 1500, valueKrw: 1950000, drawdownUsd: 0, drawdownKrw: 0 },
]

const metrics = { mdd: -46.15, mddPeakDate: '2024-03-01', mddTroughDate: '2024-04-01' }

describe('RiskChart', () => {
  it('고른 통화의 평가 가치와 고점 대비 낙폭을 날마다 고른다', () => {
    expect(riskChartData(dailyValues, 'KRW')[2]).toEqual({ date: '2024-04-01', value: 840000, drawdown: -46.15 })
    expect(riskChartData(dailyValues, 'USD')[2]).toEqual({ date: '2024-04-01', value: 600, drawdown: -50 })
  })

  it('낙폭이 없는 날은 0 으로 그린다', () => {
    expect(riskChartData([{ date: '2024-01-15', valueUsd: 1, valueKrw: 1 }], 'KRW')[0].drawdown).toBe(0)
  })

  it('자산 추이에 최대 낙폭의 고점(빨강) · 저점(파랑) 점을, 아래 차트에 낙폭을 그린다', () => {
    render(<RiskChart dailyValues={dailyValues} metrics={metrics} currency="KRW" />)

    const [value, drawdown] = readCharts()
    expect(value.lines).toEqual(['value:평가 가치'])
    expect(value.points.map((p: { value: number }) => p.value)).toEqual([1300000, 1560000, 840000, 1950000])
    expect(value.dots).toEqual([
      { x: '2024-03-01', y: 1560000, fill: 'rgb(var(--gain))' },
      { x: '2024-04-01', y: 840000, fill: 'rgb(var(--loss))' },
    ])
    expect(drawdown.lines).toEqual(['drawdown:고점 대비 낙폭'])
    expect(drawdown.points.map((p: { drawdown: number }) => p.drawdown)).toEqual([0, 0, -46.15, 0])
    expect(drawdown.yDomains).toEqual([[-47, 0]])
  })

  it('낙폭이 모두 0 이어도 낙폭 축은 0 이하로 그린다', () => {
    const flat = dailyValues.map((v) => ({ ...v, drawdownKrw: 0, drawdownUsd: 0 }))
    render(<RiskChart dailyValues={flat} metrics={{ mdd: 0 }} currency="KRW" />)

    expect(readCharts()[1].yDomains).toEqual([[-1, 0]])
  })

  it('고점 · 저점 점이 없으면 그 범례도 두지 않는다', () => {
    render(<RiskChart dailyValues={dailyValues} metrics={{ mdd: 0, mddPeakDate: null, mddTroughDate: null }} currency="KRW" />)

    expect(screen.queryByText('최대 낙폭 고점')).toBeNull()
    expect(screen.queryByText('최대 낙폭 저점')).toBeNull()
  })

  it('낙폭 차트를 고르면 고점 · 저점 점을 낙폭 차트에 찍는다', () => {
    render(<RiskChart dailyValues={dailyValues} metrics={metrics} currency="KRW" markers="drawdown" />)

    const [value, drawdown] = readCharts()
    expect(value.dots).toEqual([])
    expect(drawdown.dots).toEqual([
      { x: '2024-03-01', y: 0, fill: 'rgb(var(--gain))' },
      { x: '2024-04-01', y: -46.15, fill: 'rgb(var(--loss))' },
    ])
  })

  it('툴팁은 원화 금액과 낙폭 퍼센트로 쓴다', () => {
    render(<RiskChart dailyValues={dailyValues} metrics={metrics} currency="KRW" />)

    expect(readTooltips()).toEqual([['₩1,234,568', '평가 가치'], ['1234567.89%', '고점 대비']])
  })

  it('달러 기준이면 달러 금액으로 쓴다', () => {
    render(<RiskChart dailyValues={dailyValues} metrics={metrics} currency="USD" />)

    expect(readTooltips()[0]).toEqual(['$1234567.89', '평가 가치'])
  })

  it('일별 값이 둘보다 적으면 그리지 않는다', () => {
    const { container } = render(<RiskChart dailyValues={dailyValues.slice(0, 1)} metrics={metrics} currency="KRW" />)

    expect(container).toBeEmptyDOMElement()
  })
})
