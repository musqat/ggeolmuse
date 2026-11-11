import { describe, it, expect, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import { BandChart } from './BandChart'
import type { SignalChartData, StrategyTransaction } from '../../../services/api'
import { readCharts } from '../../../test/readCharts'

vi.mock('recharts', () => import('../../../test/rechartsProbe'))

// 첫 날은 평균이 안 참, 1-04 위 선 위에서 사고 1-08 아래 선 아래에서 팜
const chart: SignalChartData = {
  symbol: 'SPY',
  points: [
    { date: '2024-01-02', close: 470.1 },
    { date: '2024-01-03', close: 468.8, average: 469.45, upperBand: 481.19, lowerBand: 457.71 },
    { date: '2024-01-04', close: 490.2, average: 476.37, upperBand: 488.28, lowerBand: 464.46 },
    { date: '2024-01-05', close: 485.5, average: 481.5, upperBand: 493.54, lowerBand: 469.46 },
    { date: '2024-01-08', close: 455.0, average: 476.9, upperBand: 488.82, lowerBand: 464.98 },
  ],
  crosses: [],
}

const trade = (date: string, shares: number, trigger: string): StrategyTransaction => ({
  date,
  price: 1,
  shares,
  amount: 0,
  fxRate: 1300,
  trigger,
})

const transactions = [
  trade('2024-01-04', 20, '밴드 위 매수'),
  trade('2024-01-05', 0, '양도세 납부'),
  trade('2024-01-08', -20, '밴드 아래 매도'),
]

const band = { window: 200, upperRate: 0.025, lowerRate: 0.035 }

describe('BandChart', () => {
  it('수정 종가 · 평균 · 위 선 · 아래 선을 그리고 위 · 아래 선은 점선, 산 날 · 판 날은 그 날짜 수정 종가 위에 거래 점을 찍는다', () => {
    render(<BandChart chart={chart} transactions={transactions} band={band} />)

    const [signal] = readCharts()
    expect(signal.points).toEqual(chart.points)
    expect(signal.lines).toEqual(['close:수정 종가', 'average:200일선', 'upperBand:위 선 +2.5%', 'lowerBand:아래 선 -3.5%'])
    expect(signal.dashed).toEqual(['upperBand', 'lowerBand'])
    expect(signal.dots).toEqual([
      { x: '2024-01-04', y: 490.2, fill: '#8b5cf6' },
      { x: '2024-01-08', y: 455, fill: '#ef4444' },
    ])
  })

  it('차트 아래 범례 한 줄에 선 넷과 거래 점 둘의 이름을 둔다', () => {
    render(<BandChart chart={chart} transactions={transactions} band={band} />)

    const legend = screen.getByRole('list', { name: '차트 범례' })
    expect(within(legend).getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      '수정 종가',
      '200일선',
      '위 선 +2.5%',
      '아래 선 -3.5%',
      '매수',
      '매도',
    ])
  })

  it('툴팁은 날짜 · 수정 종가와 값이 있는 평균 · 선만 보인다', () => {
    const { unmount } = render(<BandChart chart={chart} transactions={transactions} band={band} />)

    const first = within(screen.getByTestId('tooltip-content'))
    expect(first.getByText('2024-01-02')).toBeInTheDocument()
    expect(first.getByText('수정 종가: $470.10')).toBeInTheDocument()
    expect(first.queryByText(/선/)).toBeNull()
    unmount()

    render(<BandChart chart={{ ...chart, points: chart.points.slice(1) }} transactions={transactions} band={band} />)

    const full = within(screen.getByTestId('tooltip-content'))
    expect(full.getByText('200일선: $469.45')).toBeInTheDocument()
    expect(full.getByText('위 선 +2.5%: $481.19')).toBeInTheDocument()
    expect(full.getByText('아래 선 -3.5%: $457.71')).toBeInTheDocument()
  })

  it('밴드 설정을 모르면 평균 · 위 선 · 아래 선으로 부르고, 차트에 없는 날의 거래는 찍지 않는다', () => {
    render(<BandChart chart={chart} transactions={[trade('2023-12-29', 5, '밴드 위 매수')]} />)

    const [signal] = readCharts()
    expect(signal.lines).toEqual(['close:수정 종가', 'average:평균', 'upperBand:위 선', 'lowerBand:아래 선'])
    expect(signal.dots).toEqual([])
  })

  it.each([
    ['값이 없으면', null],
    ['점이 하나면', { ...chart, points: chart.points.slice(0, 1) }],
  ])('%s 차트 대신 안내를 보인다', (_, value) => {
    render(<BandChart chart={value} transactions={transactions} band={band} />)

    expect(screen.getByText('이 결과에는 밴드 값이 없습니다')).toBeInTheDocument()
    expect(screen.queryByTestId('line-chart')).toBeNull()
  })
})
