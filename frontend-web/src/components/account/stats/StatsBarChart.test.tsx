import { describe, it, expect, vi } from 'vitest'
import type { ReactNode } from 'react'
import { render, screen, within } from '@testing-library/react'
import { StatsBarChart } from './StatsBarChart'
import { formatKrw, formatSigned } from '../../../utils/format'
import { formatKrwTick, formatMonthTick } from '@components/charts/backtest/shared/chartStyle'

vi.mock('recharts', async () => ({
  ...(await import('../../../test/rechartsProbe')),
  BarChart: ({ data, layout, children }: { data?: unknown[]; layout?: string; children?: ReactNode }) => (
    <div data-testid="bar-chart" data-layout={layout ?? 'horizontal'} data-points={JSON.stringify(data ?? [])}>
      {children}
    </div>
  ),
  Bar: ({ dataKey, children }: { dataKey: string; children?: ReactNode }) => (
    <div data-testid="bar" data-key={dataKey}>
      {children}
    </div>
  ),
  Cell: ({ fill }: { fill?: string }) => <i data-testid="cell" data-fill={fill} />,
  Tooltip: ({
    formatter,
    labelFormatter,
  }: {
    formatter?: (value: number, name: string) => unknown;
    labelFormatter?: (label: string) => unknown;
  }) => (
    <i
      data-testid="tooltip"
      data-sample={JSON.stringify(formatter ? formatter(1234567.891, '샘플') : null)}
      data-label={labelFormatter ? String(labelFormatter('2024-09')) : ''}
    />
  ),
  YAxis: ({ allowDecimals }: { allowDecimals?: boolean }) => (
    <i data-testid="y-axis" data-decimals={String(allowDecimals ?? true)} />
  ),
}))

const signedKrw = (value: number) => formatSigned(Math.round(value), formatKrw)

const readBars = () => {
  const chart = screen.getByTestId('bar-chart')
  return {
    layout: chart.dataset.layout,
    points: JSON.parse(chart.dataset.points ?? '[]'),
    fills: within(chart)
      .getAllByTestId('cell')
      .map((cell) => cell.dataset.fill),
    refLines: within(chart)
      .queryAllByTestId('ref-line')
      .map((line) => JSON.parse(line.dataset.ref ?? '{}')),
    tooltip: JSON.parse(within(chart).getByTestId('tooltip').dataset.sample ?? 'null'),
    tooltipLabel: within(chart).getByTestId('tooltip').dataset.label,
    decimals: within(chart).getByTestId('y-axis').dataset.decimals,
  }
}

describe('StatsBarChart', () => {
  it('세로 막대는 값마다 막대 하나에 이익 · 손실 색을 칠하고 0 에 기준선을 두며 툴팁 제목은 이름 축 서식이다', () => {
    render(
      <StatsBarChart
        data={[
          { label: '2024-09', value: 120_000 },
          { label: '2024-10', value: -30_000 },
          { label: '2024-11', value: 0 },
        ]}
        name="수익"
        formatValue={signedKrw}
        formatAxis={formatKrwTick}
        formatLabel={formatMonthTick}
      />
    )

    const bars = readBars()
    expect(bars.layout).toBe('horizontal')
    expect(bars.points).toEqual([
      { label: '2024-09', value: 120_000 },
      { label: '2024-10', value: -30_000 },
      { label: '2024-11', value: 0 },
    ])
    expect(bars.fills).toEqual(['rgb(var(--gain))', 'rgb(var(--loss))', 'rgb(var(--gain))'])
    expect(bars.refLines).toEqual([{ y: 0 }])
    expect(bars.tooltip).toEqual(['+₩1,234,568', '수익'])
    expect(bars.tooltipLabel).toBe('24.9')
    expect(bars.decimals).toBe('true')
  })

  it('개수 막대는 모든 막대를 한 색으로 칠하고 값 축 눈금을 정수로만 둔다', () => {
    render(
      <StatsBarChart
        counts
        data={[
          { label: '월', value: 3 },
          { label: '화', value: 0 },
        ]}
        name="체결 수"
        formatValue={(value) => `${value}회`}
        formatAxis={(value) => `${value}`}
      />
    )

    const bars = readBars()
    expect(bars.layout).toBe('horizontal')
    expect(bars.fills).toEqual(['var(--accent-dark)', 'var(--accent-dark)'])
    expect(bars.decimals).toBe('false')
    expect(bars.tooltip).toEqual(['1234567.891회', '체결 수'])
  })

  it('가로 막대는 이름을 세로 축에 두고 같은 색 규칙을 쓴다', () => {
    render(
      <StatsBarChart
        horizontal
        data={[
          { label: 'AAPL', value: 62.5 },
          { label: 'TSLA', value: -5.29 },
        ]}
        name="비율"
        formatValue={(value) => `${value.toFixed(2)}%`}
        formatAxis={(value) => `${value}%`}
      />
    )

    const bars = readBars()
    expect(bars.layout).toBe('vertical')
    expect(bars.fills).toEqual(['rgb(var(--gain))', 'rgb(var(--loss))'])
    expect(bars.tooltip).toEqual(['1234567.89%', '비율'])
    expect(bars.tooltipLabel).toBe('')
  })
})
