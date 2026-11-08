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
import { PurchaseDayCompare } from './PurchaseDayCompare';
import {
  addCompare,
  compareRows,
  purchaseDaySeries,
  removeCompare,
  targetOffset,
  targetSummary,
  tradingDayOnOrAfter,
} from './purchaseDays';

const ABOVE = 'rgb(var(--gain))';
const BELOW = 'rgb(var(--text-2))';
const DEFAULT_TARGET = '10';

interface PurchaseDayChartProps {
  returns?: PurchaseDayReturn[];
  currency: MetricCurrency;
  purchaseDate?: string | null;
  valuationDate?: string | null;
}

// 매수일별 수익률: 그날 샀다면 평가일까지 수익률 곡선, 목표선 위를 칠하고 목표 이상인 날 비율을 세며 찍은 날을 견줌
export const PurchaseDayChart: React.FC<PurchaseDayChartProps> = ({ returns, currency, purchaseDate, valuationDate }) => {
  const [targetText, setTargetText] = useState(DEFAULT_TARGET);
  const [compareDates, setCompareDates] = useState<string[]>([]);
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
  const compared = points.filter((p) => compareDates.includes(p.date));

  // 곡선의 날을 누르면 비교에 넣고 다시 누르면 뺌
  const toggleCompare = (date?: string) => {
    if (!date) return;
    setCompareDates((dates) => (dates.includes(date) ? removeCompare(dates, date) : addCompare(dates, date, purchaseDate)));
  };
  // 날짜 칸은 그날이나 다음 거래일을 넣음
  const addFromField = (date: string) => {
    const tradingDay = tradingDayOnOrAfter(points, date);
    if (tradingDay) setCompareDates((dates) => addCompare(dates, tradingDay, purchaseDate));
  };

  return (
    <div className="space-y-4">
      <div>
        <h4 className="text-sm font-medium text-tx-1 mb-1">
          매수일별 수익률 ({currency === 'KRW' ? '원화' : '달러'} 기준)
        </h4>
        <p className="text-xs text-tx-2">
          그날 종가에 샀다면 평가일({valuationDate ?? '-'})까지 수익률, 위 결과와 같은 수수료 · 배당 · 환율 기준
        </p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-surface rounded-xl shadow-sm p-5 border border-line/50">
          <label htmlFor={`target-${id}`} className="text-sm font-medium text-tx-2">
            목표 수익률
          </label>
          <div className="flex items-center gap-2 mt-1">
            <input
              id={`target-${id}`}
              type="number"
              step="1"
              inputMode="decimal"
              value={targetText}
              onChange={(e) => setTargetText(e.target.value)}
              className="w-24 border border-line-strong rounded-md px-3 py-1.5 bg-surface text-tx-1 focus:ring-2 focus:ring-brand focus:border-brand"
            />
            <span className="text-tx-2">%</span>
          </div>
        </div>
        <StatCard
          label="목표 이상인 날"
          value={hasTarget ? formatPercent(summary.share) : '—'}
          sub={hasTarget ? `거래일 ${summary.total}일 중 ${summary.hits}일` : undefined}
        />
        <StatCard
          label="가장 좋은 매수일"
          value={formatSigned(best.returnPct, formatPercent)}
          sub={`${best.date} 매수`}
          valueClassName={gainLossClass(best.returnPct)}
        />
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
      <ResponsiveContainer width="100%" height={240}>
        <AreaChart
          data={points}
          margin={CHART_MARGIN}
          onClick={(state: { activeLabel?: string } | null) => toggleCompare(state?.activeLabel)}
          style={{ cursor: 'pointer' }}
        >
          <CartesianGrid {...GRID_PROPS} />
          <XAxis dataKey="date" {...AXIS_PROPS} {...dateAxis(points.map((p) => p.date))} />
          <YAxis {...AXIS_PROPS} tickFormatter={(value: number) => `${Math.round(value)}%`} domain={['auto', 'auto']} />
          <Tooltip
            {...TOOLTIP_PROPS}
            labelFormatter={(label: string) => `${label} 매수`}
            formatter={(value: number) => [formatSigned(value, formatPercent), '그날 샀다면']}
          />
          <ReferenceLine y={0} stroke="rgb(var(--line-strong))" />
          {hasTarget && (
            <ReferenceLine
              y={line}
              stroke="var(--accent)"
              strokeDasharray="4 4"
              // 목표가 값 범위 밖이면 축을 늘림(축 id 를 적어야 늘어남)
              yAxisId={0}
              ifOverflow="extendDomain"
              label={{ value: `목표 ${formatPercent(line)}`, position: 'insideTopRight', fill: 'rgb(var(--text-2))', fontSize: 12 }}
            />
          )}
          <Area
            type="monotone"
            dataKey="returnPct"
            name="그날 샀다면"
            baseValue={line}
            stroke={`url(#${strokeId})`}
            fill={`url(#${fillId})`}
            strokeWidth={1.8}
            isAnimationActive={false}
          />
          {chosen && <ReferenceDot x={chosen.date} y={chosen.returnPct} fill={MARKER_COLORS.purchase} {...MARKER_PROPS} />}
          {compared.map((p) => (
            <ReferenceDot key={p.date} x={p.date} y={p.returnPct} fill={MARKER_COLORS.compare} {...MARKER_PROPS} />
          ))}
        </AreaChart>
      </ResponsiveContainer>

      <PurchaseDayCompare
        rows={compareRows(points, compareDates, purchaseDate)}
        onAdd={addFromField}
        onRemove={(date) => setCompareDates((dates) => removeCompare(dates, date))}
        min={points[0].date}
        max={points[points.length - 1].date}
      />
    </div>
  );
};
