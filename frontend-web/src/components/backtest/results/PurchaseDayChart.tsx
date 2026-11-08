import React, { useId, useMemo, useState } from 'react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ReferenceDot,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { PurchaseDayReturn } from '../../../services/api';
import { formatPercent, formatSigned } from '../../../utils/format';
import { gainLossClass } from '../../../utils/gainLoss';
import {
  AXIS_PROPS,
  CHART_MARGIN,
  GRID_PROPS,
  MARKER_COLORS,
  MARKER_PROPS,
  TOOLTIP_PROPS,
  dateAxis,
} from '@components/charts/backtest/shared/chartStyle';
import type { MetricCurrency } from './CurrencyToggle';
import { StatCard } from './StatCard';
import { purchaseDaySeries, targetOffset, targetSummary } from './purchaseDays';

const ABOVE = 'rgb(var(--gain))';
const BELOW = 'rgb(var(--text-2))';
const TARGET_COLOR = 'var(--accent)';
const DEFAULT_TARGET = '10';
const NAME = '여기서 샀다면 평가일까지';

interface PurchaseDayChartProps {
  returns?: PurchaseDayReturn[];
  currency: MetricCurrency;
  purchaseDate?: string | null;
  valuationDate?: string | null;
}

// 매수일별 수익률: 가로축 날짜에 샀다면 평가일까지 수익률 곡선, 목표선 위를 칠하고 목표 이상인 날을 셈
export const PurchaseDayChart: React.FC<PurchaseDayChartProps> = ({ returns, currency, purchaseDate, valuationDate }) => {
  const [targetText, setTargetText] = useState(DEFAULT_TARGET);
  const points = useMemo(() => purchaseDaySeries(returns, currency), [returns, currency]);
  const id = useId().replace(/[^a-zA-Z0-9_-]/g, '');
  const strokeId = `purchase-day-stroke-${id}`;
  const fillId = `purchase-day-fill-${id}`;

  if (points.length < 2) {
    return <p className="text-sm text-tx-2 py-8 text-center">매수일별 수익률을 낼 거래일이 둘보다 적습니다</p>;
  }

  const parsed = Number.parseFloat(targetText);
  const hasTarget = Number.isFinite(parsed);
  // 목표가 비면 0% 선 기준으로 칠함
  const line = hasTarget ? parsed : 0;
  const values = points.map((p) => p.returnPct);
  const offset = targetOffset(Math.min(...values), Math.max(...values), line);
  const summary = targetSummary(points, line);
  const best = summary.best as NonNullable<typeof summary.best>;
  const chosen = points.find((p) => p.date === purchaseDate);

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <p className="text-sm text-tx-2">
          가로축 날짜에 샀다면 평가일({valuationDate ?? '-'})까지 수익률({currency === 'KRW' ? '원화' : '달러'} 기준, 위
          결과와 같은 수수료 · 배당 · 환율)
        </p>
        <div className="flex items-center gap-2">
          <label htmlFor={`target-${id}`} className="text-sm text-tx-2">
            목표 수익률
          </label>
          <input
            id={`target-${id}`}
            type="number"
            step="1"
            inputMode="decimal"
            value={targetText}
            onChange={(e) => setTargetText(e.target.value)}
            className="w-20 border border-line-strong rounded-md px-2 py-1 bg-surface text-tx-1 focus:ring-2 focus:ring-brand focus:border-brand"
          />
          <span className="text-tx-2">%</span>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard label="거래일" value={`${summary.total}일`} sub="매수일부터 평가일 전까지" />
        <StatCard
          label="목표 이상인 날"
          value={hasTarget ? `${summary.hits}일` : '—'}
          sub={hasTarget ? `전체의 ${formatPercent(summary.share)}` : undefined}
        />
        <StatCard
          label="가장 좋은 매수일"
          value={best.date}
          sub={
            <p>
              평가일까지 <span className={gainLossClass(best.returnPct)}>{formatSigned(best.returnPct, formatPercent)}</span>
            </p>
          }
        />
      </div>

      <div>
        <div className="flex flex-wrap gap-4 text-xs text-tx-2 mb-1">
          {hasTarget && (
            <span className="inline-flex items-center gap-1">
              <span aria-hidden className="w-4 border-t-2 border-dashed" style={{ borderColor: TARGET_COLOR }} />
              {`목표 ${formatPercent(line)} 이상`}
            </span>
          )}
          <span className="inline-flex items-center gap-1">
            <span aria-hidden className="w-2.5 h-2.5 rounded-full" style={{ background: MARKER_COLORS.purchase }} />
            이 결과의 매수일
          </span>
        </div>
        <svg width="0" height="0" className="absolute" aria-hidden>
          <defs>
            <linearGradient id={strokeId} x1="0" y1="0" x2="0" y2="1">
              <stop offset={offset} stopColor={ABOVE} />
              <stop offset={offset} stopColor={BELOW} />
            </linearGradient>
            <linearGradient id={fillId} x1="0" y1="0" x2="0" y2="1">
              <stop offset={offset} stopColor={ABOVE} stopOpacity={0.25} />
              <stop offset={offset} stopColor={ABOVE} stopOpacity={0} />
            </linearGradient>
          </defs>
        </svg>
        <ResponsiveContainer width="100%" height={260}>
          <AreaChart data={points} margin={CHART_MARGIN}>
            <CartesianGrid {...GRID_PROPS} />
            <XAxis dataKey="date" {...AXIS_PROPS} {...dateAxis(points.map((p) => p.date))} />
            <YAxis {...AXIS_PROPS} tickFormatter={(value: number) => `${Math.round(value)}%`} domain={['auto', 'auto']} />
            <Tooltip
              {...TOOLTIP_PROPS}
              labelFormatter={(label: string) => `${label} 매수`}
              formatter={(value: number) => [formatSigned(value, formatPercent), NAME]}
            />
            <ReferenceLine y={0} stroke="rgb(var(--line-strong))" />
            {hasTarget && (
              <ReferenceLine
                y={line}
                stroke={TARGET_COLOR}
                strokeDasharray="4 4"
                // 목표가 값 범위 밖이면 축을 늘림(축 id 를 적어야 늘어남)
                yAxisId={0}
                ifOverflow="extendDomain"
              />
            )}
            <Area
              type="monotone"
              dataKey="returnPct"
              name={NAME}
              baseValue={line}
              stroke={`url(#${strokeId})`}
              fill={`url(#${fillId})`}
              strokeWidth={1.8}
              isAnimationActive={false}
            />
            {chosen && <ReferenceDot x={chosen.date} y={chosen.returnPct} fill={MARKER_COLORS.purchase} {...MARKER_PROPS} />}
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};
