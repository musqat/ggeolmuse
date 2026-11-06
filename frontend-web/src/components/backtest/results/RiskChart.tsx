import React, { useMemo } from 'react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  Line,
  LineChart,
  ReferenceDot,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { DailyValue, PerformanceMetrics } from '../../../services/api';
import { riskChartData } from './riskChartData';
import { formatKrw, formatUsd } from '../../../utils/format';
import { ChartCard } from '@components/charts/backtest/shared/ChartPanel';
import {
  AXIS_PROPS,
  CHART_MARGIN,
  GRID_PROPS,
  MARKER_PROPS,
  TOOLTIP_PROPS,
  dateAxis,
  formatKrwTick,
  formatUsdTick,
} from '@components/charts/backtest/shared/chartStyle';
import type { MetricCurrency } from './CurrencyToggle';

const PEAK_COLOR = 'rgb(var(--gain))';
const TROUGH_COLOR = 'rgb(var(--loss))';

interface RiskChartProps {
  dailyValues?: DailyValue[];
  metrics?: PerformanceMetrics | null;
  currency: MetricCurrency;
  // 최대 낙폭 고점 · 저점 점을 찍을 차트
  markers?: 'value' | 'drawdown';
  subtitle?: string;
}

// 서버 일별 값으로 그린 자산 추이와 그 아래 고점 대비 낙폭(최대 낙폭 고점 · 저점 점)
export const RiskChart: React.FC<RiskChartProps> = ({ dailyValues, metrics, currency, markers = 'value', subtitle }) => {
  const data = useMemo(() => riskChartData(dailyValues ?? [], currency), [dailyValues, currency]);
  if (data.length < 2) return null;

  const xAxis = dateAxis(data.map((d) => d.date));
  const money = currency === 'KRW' ? (value: number) => formatKrw(Math.round(value)) : formatUsd;
  const peak = data.find((d) => d.date === metrics?.mddPeakDate);
  const trough = data.find((d) => d.date === metrics?.mddTroughDate);
  // 낙폭 축은 가장 깊은 낙폭을 내림한 값 ~ 0(모두 0 이어도 -1 ~ 0)
  const deepest = Math.floor(Math.min(-1, ...data.map((d) => d.drawdown)));

  return (
    <ChartCard title="자산 추이 · 고점 대비 낙폭" subtitle={subtitle}>
      {(peak || trough) && (
        <div className="flex gap-4 text-xs text-tx-2 mb-2">
          <span className="flex items-center gap-1">
            <span className="w-2.5 h-2.5 rounded-full bg-gain" />최대 낙폭 고점
          </span>
          <span className="flex items-center gap-1">
            <span className="w-2.5 h-2.5 rounded-full bg-loss" />최대 낙폭 저점
          </span>
        </div>
      )}
      <ResponsiveContainer width="100%" height={240}>
        <LineChart data={data} margin={CHART_MARGIN} syncId="backtest-risk">
          <CartesianGrid {...GRID_PROPS} />
          <XAxis dataKey="date" {...AXIS_PROPS} {...xAxis} />
          <YAxis {...AXIS_PROPS} tickFormatter={currency === 'KRW' ? formatKrwTick : formatUsdTick} domain={['auto', 'auto']} />
          <Tooltip {...TOOLTIP_PROPS} formatter={(value: number) => [money(value), '평가 가치']} />
          <Line type="monotone" dataKey="value" name="평가 가치" stroke="#f59e0b" strokeWidth={2} dot={false} />
          {markers === 'value' && peak && <ReferenceDot {...MARKER_PROPS} x={peak.date} y={peak.value} fill={PEAK_COLOR} />}
          {markers === 'value' && trough && (
            <ReferenceDot {...MARKER_PROPS} x={trough.date} y={trough.value} fill={TROUGH_COLOR} />
          )}
        </LineChart>
      </ResponsiveContainer>
      <ResponsiveContainer width="100%" height={120}>
        <AreaChart data={data} margin={CHART_MARGIN} syncId="backtest-risk">
          <CartesianGrid {...GRID_PROPS} />
          <XAxis dataKey="date" {...AXIS_PROPS} {...xAxis} />
          <YAxis {...AXIS_PROPS} tickFormatter={(value: number) => `${value}%`} domain={[deepest, 0]} />
          <Tooltip {...TOOLTIP_PROPS} formatter={(value: number) => [`${value.toFixed(2)}%`, '고점 대비']} />
          <Area
            type="monotone"
            dataKey="drawdown"
            name="고점 대비 낙폭"
            stroke={TROUGH_COLOR}
            fill={TROUGH_COLOR}
            fillOpacity={0.15}
          />
          {markers === 'drawdown' && peak && (
            <ReferenceDot {...MARKER_PROPS} x={peak.date} y={peak.drawdown} fill={PEAK_COLOR} />
          )}
          {markers === 'drawdown' && trough && (
            <ReferenceDot {...MARKER_PROPS} x={trough.date} y={trough.drawdown} fill={TROUGH_COLOR} />
          )}
        </AreaChart>
      </ResponsiveContainer>
    </ChartCard>
  );
};
