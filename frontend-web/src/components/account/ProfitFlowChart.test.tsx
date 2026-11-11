import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { ProfitFlowChart, type MarketCompare } from './ProfitFlowChart'
import { readCharts, readTooltips, readXAxes } from '../../test/readCharts'
import type { MarketComparisonResponse, ProfitFlowResponse } from '../../services/api'

vi.mock('recharts', async () => {
  const probe = await import('../../test/rechartsProbe')
  return {
    ...probe,
    // 선 색 · 점선 · 범례 모양 · 빈 값 잇기도 남기는 선 대역
    Line: ({
      dataKey,
      name,
      stroke,
      strokeDasharray,
      legendType,
      connectNulls,
    }: {
      dataKey: string
      name?: string
      stroke?: string
      strokeDasharray?: string
      legendType?: string
      connectNulls?: boolean
    }) => (
      <i
        data-testid="line"
        data-key={dataKey}
        data-name={name ?? ''}
        data-style={JSON.stringify({
          stroke,
          strokeDasharray: strokeDasharray ?? null,
          legendType: legendType ?? null,
          connectNulls: connectNulls ?? null,
        })}
      />
    ),
    // 세로축 위아래 여백을 남기는 대역
    YAxis: ({ padding }: { padding?: { top?: number; bottom?: number } }) => (
      <i data-testid="y-axis" data-padding={JSON.stringify(padding ?? null)} />
    ),
    // 범례 글자 서식에 선 이름 하나를 넣어 그린 대역
    Legend: ({ formatter }: { formatter?: (value: string) => ReactNode }) => (
      <i data-testid="legend">{formatter ? formatter('내 계좌') : null}</i>
    ),
  }
})

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

const MARKET: MarketComparisonResponse = {
  symbol: 'SPY',
  points: [
    { date: '2026-07-01', profitKrw: 50_000 },
    { date: '2026-07-02', profitKrw: 120_000 },
    { date: '2026-07-03', profitKrw: 90_000 },
    { date: '2026-07-06', profitKrw: 310_000 },
  ],
  currentProfitKrw: 310_000,
}

const compare = (parts: Partial<MarketCompare> = {}): MarketCompare => ({
  on: true,
  onToggle: vi.fn(),
  status: 'success',
  data: MARKET,
  onRetry: vi.fn(),
  ...parts,
})

const lineStyles = () =>
  screen.getAllByTestId('line').map((line) => JSON.parse(line.dataset.style ?? '{}'))

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
    expect(readTooltips()[0]).toEqual(['+₩1,234,568', '샘플'])
    expect(screen.queryByTestId('legend')).toBeNull()
    expect(screen.queryByRole('button', { name: 'S&P 500 비교' })).toBeNull()
  })

  it('최고 · 최저 날에 수익 색 · 손실 색 점을 찍는다', () => {
    render(<ProfitFlowChart flow={FLOW} />)

    expect(readCharts()[0].dots).toEqual([
      { x: '2026-07-02', y: 400_000, fill: 'rgb(var(--gain))' },
      { x: '2026-07-03', y: -50_000, fill: 'rgb(var(--loss))' },
    ])
  })

  it('세로축 위아래에 최고 · 최저 이름표 자리를 두어 맨 아래 점의 이름표가 가로축 글자와 겹치지 않는다', () => {
    render(<ProfitFlowChart flow={FLOW} />)

    expect(JSON.parse(screen.getByTestId('y-axis').dataset.padding ?? 'null')).toEqual({ top: 24, bottom: 24 })
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

  it('S&P 500 비교 단추는 제목 줄 오른쪽에 있고 꺼져 있으면 SPY 선과 비교 한 줄이 없으며 누르면 켜기를 알린다', async () => {
    const user = userEvent.setup()
    const onToggle = vi.fn()
    render(
      <ProfitFlowChart flow={FLOW} compare={compare({ on: false, status: 'pending', data: undefined, onToggle })} />
    )

    const button = screen.getByRole('button', { name: 'S&P 500 비교' })
    expect(button).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByRole('heading', { name: '수익 흐름' }).parentElement).toContainElement(button)
    expect(readCharts()[0].lines).toEqual(['profitKrw:수익'])
    expect(screen.queryByTestId('legend')).toBeNull()
    expect(screen.queryByText(/S&P 500 시세|같은 날짜에 같은 돈을/)).toBeNull()

    await user.click(button)

    expect(onToggle).toHaveBeenCalledTimes(1)
  })

  it('켜면 SPY 수익을 같은 날짜에 회색 점선으로 겹치고 범례 · 툴팁은 내 계좌 · S&P 500 이름을 쓴다', () => {
    render(<ProfitFlowChart flow={FLOW} compare={compare()} />)

    const [chart] = readCharts()
    expect(screen.getByRole('button', { name: 'S&P 500 비교' })).toHaveAttribute('aria-pressed', 'true')
    expect(
      chart.points.map((p: { date: string; profitKrw: number; marketKrw: number }) => [p.date, p.profitKrw, p.marketKrw])
    ).toEqual([
      ['2026-07-01', 100_000, 50_000],
      ['2026-07-02', 400_000, 120_000],
      ['2026-07-03', -50_000, 90_000],
      ['2026-07-06', 250_000, 310_000],
    ])
    expect(chart.lines).toEqual(['profitKrw:내 계좌', 'marketKrw:S&P 500'])
    expect(lineStyles()).toEqual([
      { stroke: '#f59e0b', strokeDasharray: null, legendType: 'plainline', connectNulls: null },
      { stroke: 'rgb(var(--text-2))', strokeDasharray: '6 4', legendType: 'plainline', connectNulls: true },
    ])
    expect(screen.getByTestId('legend').firstElementChild).toHaveClass('text-tx-1')
    expect(screen.getByTestId('legend')).toHaveTextContent('내 계좌')
    expect(readTooltips()[0]).toEqual(['+₩1,234,568', '샘플'])
  })

  it('시장 비교에 없는 수익 흐름 날짜는 SPY 값을 비우고 SPY 점선은 그 날짜를 건너 앞뒤 점을 잇는다', () => {
    const market = { ...MARKET, points: MARKET.points.filter((point) => point.date !== '2026-07-03') }
    render(<ProfitFlowChart flow={FLOW} compare={compare({ data: market })} />)

    const [chart] = readCharts()
    expect(chart.points.map((p: { date: string; marketKrw: number | null }) => [p.date, p.marketKrw])).toEqual([
      ['2026-07-01', 50_000],
      ['2026-07-02', 120_000],
      ['2026-07-03', null],
      ['2026-07-06', 310_000],
    ])
    expect(lineStyles()[1].connectNulls).toBe(true)
  })

  it('그래프 아래에 같은 돈을 SPY 에 넣었을 때의 현재 수익과 내 계좌와의 차이를 보인다', () => {
    const { rerender } = render(<ProfitFlowChart flow={FLOW} compare={compare()} />)

    const line = screen.getByText(/같은 날짜에 같은 돈을/)
    expect(line).toHaveTextContent('같은 날짜에 같은 돈을 S&P 500(SPY)에 넣었다면 +₩310,000 (내 계좌보다 +₩60,000)')
    expect(screen.getByText('+₩310,000')).toHaveClass('text-gain')

    rerender(
      <ProfitFlowChart
        flow={FLOW}
        compare={compare({ data: { ...MARKET, currentProfitKrw: -20_000 } })}
      />
    )

    expect(screen.getByText(/같은 날짜에 같은 돈을/)).toHaveTextContent(
      '같은 날짜에 같은 돈을 S&P 500(SPY)에 넣었다면 -₩20,000 (내 계좌보다 -₩270,000)'
    )
    expect(screen.getByText('-₩20,000')).toHaveClass('text-loss')
  })

  it('시장 비교 점이 비면 SPY 선 없이 시세를 불러오지 못했다는 한 줄을 보인다', () => {
    render(
      <ProfitFlowChart flow={FLOW} compare={compare({ data: { symbol: 'SPY', points: [], currentProfitKrw: 0 } })} />
    )

    expect(screen.getByText('S&P 500 시세를 불러오지 못했습니다')).toBeInTheDocument()
    expect(readCharts()[0].lines).toEqual(['profitKrw:수익'])
    expect(screen.queryByTestId('legend')).toBeNull()
    expect(screen.queryByText(/같은 날짜에 같은 돈을/)).toBeNull()
  })

  it('불러오는 중에는 안내 한 줄, 못 받으면 오류와 다시 시도 단추를 보인다', async () => {
    const user = userEvent.setup()
    const onRetry = vi.fn()
    const { rerender } = render(
      <ProfitFlowChart flow={FLOW} compare={compare({ status: 'pending', data: undefined })} />
    )

    expect(screen.getByText('S&P 500 시세를 불러오는 중입니다')).toBeInTheDocument()
    expect(readCharts()[0].lines).toEqual(['profitKrw:수익'])

    rerender(<ProfitFlowChart flow={FLOW} compare={compare({ status: 'error', data: undefined, onRetry })} />)

    expect(screen.getByText(/S&P 500 비교를 불러오지 못했습니다/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(onRetry).toHaveBeenCalledTimes(1)
  })
})
