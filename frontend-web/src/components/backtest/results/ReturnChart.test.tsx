import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ReturnChart } from './ReturnChart'
import { zeroOffset } from './resultValues'
import { readCharts, readTooltips } from '../../../test/readCharts'

vi.mock('recharts', () => import('../../../test/rechartsProbe'))

const dailyValues = [
  { date: '2024-01-12', valueUsd: 0, valueKrw: 0 },
  { date: '2024-01-15', valueUsd: 1000, valueKrw: 1300000 },
  { date: '2024-02-15', valueUsd: 900, valueKrw: 1170000 },
  { date: '2024-03-15', valueUsd: 1200, valueKrw: 1560000 },
]
const deposits = [{ date: '2024-01-15', krw: 1300000, usd: 1000 }]

describe('ReturnChart', () => {
  it('넣은 돈이 생긴 날부터 평가 가치 ÷ 넣은 돈 - 1 을 % 로 그린다', () => {
    render(<ReturnChart dailyValues={dailyValues} deposits={deposits} currency="KRW" />)

    const [chart] = readCharts()
    expect(chart.points.map((p: { date: string; returnPct: number }) => [p.date, Math.round(p.returnPct)])).toEqual([
      ['2024-01-15', 0],
      ['2024-02-15', -10],
      ['2024-03-15', 20],
    ])
    expect(chart.lines).toEqual(['returnPct:수익률'])
    expect(readTooltips()[0]).toEqual(['+1234567.89%', '수익률'])
  })

  it('일별 값이 둘보다 적으면 안내를 보인다', () => {
    render(<ReturnChart dailyValues={dailyValues.slice(0, 2)} deposits={deposits} currency="KRW" />)

    expect(screen.getByText('이 결과에는 일별 값이 없습니다')).toBeInTheDocument()
  })
})

describe('zeroOffset', () => {
  it('0 이 범위 안이면 위쪽 비율, 모두 이익이면 1, 모두 손실이면 0', () => {
    expect(zeroOffset(-10, 30)).toBe(0.75)
    expect(zeroOffset(5, 30)).toBe(1)
    expect(zeroOffset(-30, -5)).toBe(0)
  })
})
