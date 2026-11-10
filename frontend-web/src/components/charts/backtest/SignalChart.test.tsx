import { describe, it, expect, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import { SignalChart } from './SignalChart'
import type { SignalChartData } from '../../../services/api'
import { readCharts } from '../../../test/readCharts'

vi.mock('recharts', () => import('../../../test/rechartsProbe'))

// 첫 날은 평균이 안 참, 1-05 골든 · 1-09 데드 크로스
const chart: SignalChartData = {
  symbol: 'AAPL',
  points: [
    { date: '2024-01-02', close: 185.64 },
    { date: '2024-01-03', close: 184.25, shortAverage: 184.95 },
    { date: '2024-01-04', close: 181.91, shortAverage: 183.08, longAverage: 183.93 },
    { date: '2024-01-05', close: 190.1, shortAverage: 186.01, longAverage: 185.42 },
    { date: '2024-01-08', close: 185.2, shortAverage: 187.65, longAverage: 186.1 },
    { date: '2024-01-09', close: 170.3, shortAverage: 177.75, longAverage: 181.84 },
  ],
  crosses: [
    { date: '2024-01-05', type: 'GOLDEN' },
    { date: '2024-01-09', type: 'DEAD' },
  ],
}

const windows = { short: 20, long: 60 }

describe('SignalChart', () => {
  it('수정 종가 · 짧은 선 · 긴 선을 기간 이름으로 그리고, 교차 점을 그 날짜 짧은 선 값 위에 골든은 이익 색 · 데드는 손실 색으로 찍는다', () => {
    render(<SignalChart chart={chart} windows={windows} />)

    const [signal] = readCharts()
    expect(signal.points).toEqual(chart.points)
    expect(signal.lines).toEqual(['close:수정 종가', 'shortAverage:20일선', 'longAverage:60일선'])
    expect(signal.dots).toEqual([
      { x: '2024-01-05', y: 186.01, fill: 'rgb(var(--gain))' },
      { x: '2024-01-09', y: 177.75, fill: 'rgb(var(--loss))' },
    ])
  })

  it('차트 아래 범례 한 줄에 선 셋과 교차 점 둘의 이름을 둔다', () => {
    render(<SignalChart chart={chart} windows={windows} />)

    const legend = screen.getByRole('list', { name: '차트 범례' })
    expect(within(legend).getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      '수정 종가',
      '20일선',
      '60일선',
      '골든 크로스',
      '데드 크로스',
    ])
  })

  it('툴팁은 날짜 · 수정 종가와 값이 있는 평균만 보인다', () => {
    const { unmount } = render(<SignalChart chart={chart} windows={windows} />)

    const first = within(screen.getByTestId('tooltip-content'))
    expect(first.getByText('2024-01-02')).toBeInTheDocument()
    expect(first.getByText('수정 종가: $185.64')).toBeInTheDocument()
    expect(first.queryByText(/일선/)).toBeNull()
    unmount()

    render(<SignalChart chart={{ ...chart, points: chart.points.slice(2) }} windows={windows} />)

    const full = within(screen.getByTestId('tooltip-content'))
    expect(full.getByText('20일선: $183.08')).toBeInTheDocument()
    expect(full.getByText('60일선: $183.93')).toBeInTheDocument()
  })

  it('기간을 모르면 짧은 선 · 긴 선으로 부르고, 짧은 선 값이 없는 날의 교차는 찍지 않는다', () => {
    render(<SignalChart chart={{ ...chart, crosses: [{ date: '2024-01-02', type: 'DEAD' }] }} />)

    const [signal] = readCharts()
    expect(signal.lines).toEqual(['close:수정 종가', 'shortAverage:짧은 선', 'longAverage:긴 선'])
    expect(signal.dots).toEqual([])
  })

  it.each([
    ['값이 없으면', null],
    ['점이 하나면', { ...chart, points: chart.points.slice(0, 1) }],
  ])('%s 차트 대신 안내를 보인다', (_, value) => {
    render(<SignalChart chart={value} windows={windows} />)

    expect(screen.getByText('이 결과에는 이동평균 값이 없습니다')).toBeInTheDocument()
    expect(screen.queryByTestId('line-chart')).toBeNull()
  })
})
