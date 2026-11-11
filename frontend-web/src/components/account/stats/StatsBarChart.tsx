import React from 'react';
import { Bar, BarChart, CartesianGrid, Cell, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import {
  AXIS_PROPS,
  CHART_HEIGHT,
  CHART_MARGIN,
  GRID_PROPS,
  TOOLTIP_PROPS,
} from '@components/charts/backtest/shared/chartStyle';

const GAIN_COLOR = 'rgb(var(--gain))';
const LOSS_COLOR = 'rgb(var(--loss))';

// 가로 막대 한 줄 높이와 값 축 자리
const ROW_HEIGHT = 32;
const VALUE_AXIS_HEIGHT = 40;

export interface StatsBar {
  label: string;
  value: number;
}

interface StatsBarChartProps {
  data: StatsBar[];
  // 툴팁 값 이름
  name: string;
  formatValue: (value: number) => string;
  formatAxis: (value: number) => string;
  // 이름 축 눈금 · 툴팁 제목 서식
  formatLabel?: (label: string) => string;
  // 이름 축을 세로로 둔 가로 막대
  horizontal?: boolean;
}

// 막대마다 이익 · 손실 색인 막대 그래프(0 에 기준선)
export const StatsBarChart: React.FC<StatsBarChartProps> = ({
  data,
  name,
  formatValue,
  formatAxis,
  formatLabel,
  horizontal = false,
}) => {
  const valueAxis = { ...AXIS_PROPS, type: 'number' as const, tickFormatter: formatAxis };
  const labelAxis = { ...AXIS_PROPS, type: 'category' as const, dataKey: 'label', tickFormatter: formatLabel };
  return (
    <ResponsiveContainer
      width="100%"
      height={horizontal ? data.length * ROW_HEIGHT + VALUE_AXIS_HEIGHT : CHART_HEIGHT}
    >
      <BarChart data={data} layout={horizontal ? 'vertical' : 'horizontal'} margin={CHART_MARGIN}>
        <CartesianGrid {...GRID_PROPS} horizontal={!horizontal} vertical={horizontal} />
        <XAxis {...(horizontal ? valueAxis : labelAxis)} />
        <YAxis {...(horizontal ? labelAxis : valueAxis)} width={horizontal ? 56 : 64} />
        <Tooltip
          {...TOOLTIP_PROPS}
          cursor={{ fill: 'rgb(var(--line) / 0.3)' }}
          formatter={(value: number) => [formatValue(value), name]}
          labelFormatter={formatLabel ? (label) => formatLabel(String(label)) : undefined}
        />
        <ReferenceLine {...(horizontal ? { x: 0 } : { y: 0 })} stroke="rgb(var(--line-strong))" />
        <Bar dataKey="value" name={name} maxBarSize={40}>
          {data.map((bar) => (
            <Cell key={bar.label} fill={bar.value >= 0 ? GAIN_COLOR : LOSS_COLOR} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
};
