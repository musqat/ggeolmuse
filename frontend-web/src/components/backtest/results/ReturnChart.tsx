import React, { useId, useMemo } from 'react';
import { Area, AreaChart, CartesianGrid, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { DailyValue } from '../../../services/api';
import { formatPercent, formatSigned } from '../../../utils/format';
import { AXIS_PROPS, CHART_MARGIN, GRID_PROPS, TOOLTIP_PROPS, dateAxis } from '@components/charts/backtest/shared/chartStyle';
import { assetSeries, type Deposit } from './assetSeries';
import type { MetricCurrency } from './CurrencyToggle';
import { zeroOffset } from './resultValues';

const GAIN = 'rgb(var(--gain))';
const LOSS = 'rgb(var(--loss))';

interface ReturnChartProps {
  dailyValues?: DailyValue[];
  deposits: Deposit[];
  currency: MetricCurrency;
}

// 그날 평가 가치 ÷ 그때까지 넣은 돈 - 1(0 위 이익 · 아래 손실)
export const ReturnChart: React.FC<ReturnChartProps> = ({ dailyValues, deposits, currency }) => {
  const data = useMemo(
    () => assetSeries(dailyValues ?? [], deposits, currency).filter((p) => p.returnPct != null),
    [dailyValues, deposits, currency],
  );
  const gradientId = `return-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`;

  if (data.length < 2) {
    return <p className="text-sm text-tx-2 py-8 text-center">이 결과에는 일별 값이 없습니다</p>;
  }

  const values = data.map((p) => p.returnPct as number);
  const offset = zeroOffset(Math.min(...values), Math.max(...values));

  return (
    <div>
      <h4 className="text-sm font-medium text-tx-1 mb-1">수익률</h4>
      <p className="text-xs text-tx-2 mb-2">평가 가치 ÷ 그때까지 넣은 돈 - 1, 0 위는 이익 · 아래는 손실</p>
      <svg width="0" height="0" className="absolute" aria-hidden>
        <defs>
          <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
            <stop offset={offset} stopColor={GAIN} />
            <stop offset={offset} stopColor={LOSS} />
          </linearGradient>
        </defs>
      </svg>
      <ResponsiveContainer width="100%" height={200}>
        <AreaChart data={data} margin={CHART_MARGIN}>
          <CartesianGrid {...GRID_PROPS} />
          <XAxis dataKey="date" {...AXIS_PROPS} {...dateAxis(data.map((p) => p.date))} />
          <YAxis {...AXIS_PROPS} tickFormatter={(value: number) => `${Math.round(value)}%`} domain={['auto', 'auto']} />
          <Tooltip {...TOOLTIP_PROPS} formatter={(value: number) => [formatSigned(value, formatPercent), '수익률']} />
          <ReferenceLine y={0} stroke="rgb(var(--line-strong))" />
          <Area
            type="monotone"
            dataKey="returnPct"
            name="수익률"
            stroke={`url(#${gradientId})`}
            fill={`url(#${gradientId})`}
            fillOpacity={0.2}
            strokeWidth={1.8}
            isAnimationActive={false}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
};
