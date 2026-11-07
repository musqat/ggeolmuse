import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ReturnLinesChart } from './ReturnLinesChart'
import { bestKeys, multipleAxis, multipleLabel, type CompareRow } from './compareRows'
import { readCharts, readTooltips } from '../../../../test/readCharts'

vi.mock('recharts', () => import('../../../../test/rechartsProbe'))

const series = [
  { key: 'A', name: 'A', color: '#3b82f6', points: [{ date: '2024-01-02', pct: 0 }, { date: '2024-02-01', pct: 100 }] },
  { key: 'B', name: 'B', color: '#ef4444', points: [{ date: '2024-01-02', pct: 0 }, { date: '2024-02-01', pct: -20 }], dots: ['2024-01-02'] },
  // 점이 하나뿐이면 그리지 않음
  { key: 'C', name: 'C', color: '#10b981', points: [{ date: '2024-01-02', pct: 0 }] },
]

describe('ReturnLinesChart', () => {
  it('배수로 바꿔 날짜별로 합치고 점이 둘 이상인 항목만 그린다', () => {
    render(<ReturnLinesChart title="수익률 추이" subtitle="" series={series} scale="multiple" />)

    const [chart] = readCharts()
    expect(chart.points).toEqual([
      { date: '2024-01-02', A: 1, B: 1 },
      { date: '2024-02-01', A: 2, B: 0.8 },
    ])
    expect(chart.lines).toEqual(['A:A', 'B:B'])
    expect(chart.dots).toEqual([{ x: '2024-01-02', y: 1, fill: '#ef4444' }])
    expect(readTooltips()[0]).toEqual(['+123456689.10%', '샘플'])
  })

  it('범례를 누르면 그 선을 끄고 다시 누르면 켠다', async () => {
    render(<ReturnLinesChart title="수익률 추이" subtitle="" series={series} scale="linear" />)

    await userEvent.click(screen.getByRole('button', { name: 'A' }))
    expect(screen.getByRole('button', { name: 'A' })).toHaveAttribute('aria-pressed', 'false')
    expect(readCharts()[0].lines).toEqual(['B:B'])

    await userEvent.click(screen.getByRole('button', { name: 'A' }))
    expect(readCharts()[0].lines).toEqual(['A:A', 'B:B'])
  })

  it('펼친 항목의 선이 없으면 다른 선을 흐리게 하지 않는다', async () => {
    render(<ReturnLinesChart title="수익률 추이" subtitle="" series={series} scale="linear" highlightedKey="C" />)

    expect(screen.getAllByTestId('line').map((line) => line.dataset.opacity)).toEqual(['1', '1'])
  })

  it('그릴 항목이 없으면 안내를 보인다', () => {
    render(<ReturnLinesChart title="수익률 추이" subtitle="" series={[series[2]]} scale="linear" />)

    expect(screen.getByText('이 결과에는 일별 값이 없습니다')).toBeInTheDocument()
  })
})

describe('multipleAxis', () => {
  it('2배 넘게 벌어지면 로그 눈금, 아니면 보통 간격을 쓴다', () => {
    expect(multipleAxis([0.8, 3.5])).toMatchObject({ scale: 'log', ticks: [1, 2] })
    expect(multipleAxis([0.9, 1.5])).toMatchObject({ scale: 'linear' })
  })

  it('2배 넘게 벌어져도 배수 눈금이 둘보다 적으면 보통 간격을 쓴다', () => {
    expect(multipleAxis([0.7, 1, 1.6])).toMatchObject({ scale: 'linear', ticks: undefined })
  })

  it('눈금 글자를 수익률 % 로 보인다', () => {
    expect(multipleLabel(2)).toBe('+100%')
    expect(multipleLabel(1)).toBe('0%')
    expect(multipleLabel(0.5)).toBe('-50%')
  })
})

describe('bestKeys', () => {
  const row = (key: string, value: number | null) => ({ key, totalReturnPercent: value }) as CompareRow

  it('값이 있는 줄이 둘 이상일 때 가장 큰 줄을 고르고 같으면 모두 고른다', () => {
    expect([...bestKeys([row('a', 1), row('b', 3), row('c', 3)], (r) => r.totalReturnPercent)]).toEqual(['b', 'c'])
    expect(bestKeys([row('a', 1), row('b', null)], (r) => r.totalReturnPercent).size).toBe(0)
  })
})
