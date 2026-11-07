import React, { useMemo } from 'react';
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { DailyValue } from '../../../services/api';
import { formatKrw, formatPercent, formatSigned, formatUsd } from '../../../utils/format';
import { ChartCard } from '@components/charts/backtest/shared/ChartPanel';
import {
  AXIS_PROPS,
  CHART_MARGIN,
  GRID_PROPS,
  MARKER_COLORS,
  TOOLTIP_PROPS,
  dateAxis,
  formatKrwTick,
  formatUsdTick,
} from '@components/charts/backtest/shared/chartStyle';
import { markerDots } from '@components/charts/backtest/shared/markers';
import { assetSeries, depositPoints, type AssetPoint, type Deposit } from './assetSeries';
import type { MetricCurrency } from './CurrencyToggle';

const VALUE_COLOR = '#f59e0b';
const INVESTED_COLOR = 'rgb(var(--text-2))';

interface AssetChartProps {
  dailyValues?: DailyValue[];
  deposits: Deposit[];
  currency: MetricCurrency;
}

// 서버 일별 값으로 그린 평가 가치와 그때까지 넣은 돈(점은 돈을 넣은 날)
export const AssetChart: React.FC<AssetChartProps> = ({ dailyValues, deposits, currency }) => {
  const data = useMemo(() => assetSeries(dailyValues ?? [], deposits, currency), [dailyValues, deposits, currency]);
  const buys = useMemo(() => depositPoints(data, deposits), [data, deposits]);

  if (data.length < 2) {
    return (
      <ChartCard title="자산 추이">
        <p className="text-sm text-tx-2 py-8 text-center">이 결과에는 일별 값이 없습니다</p>
      </ChartCard>
    );
  }

  const money = currency === 'KRW' ? (value: number) => formatKrw(Math.round(value)) : formatUsd;
  const tooltip = (value: number, name: string, item?: { payload?: AssetPoint }) => {
    const pct = item?.payload?.returnPct;
    if (name === '평가 가치' && pct != null) return [`${money(value)} (${formatSigned(pct, formatPercent)})`, name];
    return [money(value), name];
  };

  return (
    <ChartCard title="자산 추이" subtitle="평가 가치와 그때까지 넣은 돈, 점은 돈을 넣은 날">
      <div className="flex flex-wrap gap-4 text-xs text-tx-2 mb-2">
        <span className="flex items-center gap-1">
          <span className="w-4 h-0.5" style={{ background: VALUE_COLOR }} />평가 가치
        </span>
        <span className="flex items-center gap-1">
          <span className="w-4 border-t-2 border-dashed" style={{ borderColor: INVESTED_COLOR }} />누적 투자금
        </span>
        <span className="flex items-center gap-1">
          <span className="w-2.5 h-2.5 rounded-full" style={{ background: MARKER_COLORS.purchase }} />입금
        </span>
      </div>
      <ResponsiveContainer width="100%" height={260}>
        <LineChart data={data} margin={CHART_MARGIN}>
          <CartesianGrid {...GRID_PROPS} />
          <XAxis dataKey="date" {...AXIS_PROPS} {...dateAxis(data.map((d) => d.date))} />
          <YAxis {...AXIS_PROPS} tickFormatter={currency === 'KRW' ? formatKrwTick : formatUsdTick} domain={['auto', 'auto']} />
          <Tooltip {...TOOLTIP_PROPS} formatter={tooltip} />
          <Line type="monotone" dataKey="value" name="평가 가치" stroke={VALUE_COLOR} strokeWidth={2} dot={false} isAnimationActive={false} />
          <Line
            type="stepAfter"
            dataKey="invested"
            name="누적 투자금"
            stroke={INVESTED_COLOR}
            strokeWidth={1.5}
            strokeDasharray="5 5"
            dot={false}
            connectNulls
            isAnimationActive={false}
          />
          {markerDots(buys, 'invested', MARKER_COLORS.purchase, 'buy')}
        </LineChart>
      </ResponsiveContainer>
    </ChartCard>
  );
};
