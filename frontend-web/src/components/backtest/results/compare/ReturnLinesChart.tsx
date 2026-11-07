import React, { useMemo, useState } from 'react';
import { CartesianGrid, Line, LineChart, ReferenceDot, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { ChartCard } from '@components/charts/backtest/shared/ChartPanel';
import { AXIS_PROPS, CHART_MARGIN, GRID_PROPS, TOOLTIP_PROPS, dateAxis } from '@components/charts/backtest/shared/chartStyle';
import { formatPercent, formatSigned } from '../../../../utils/format';
import { multipleAxis, multipleLabel, type ReturnPoint } from './compareRows';

export interface ReturnSeries {
  key: string;
  name: string;
  color: string;
  points: ReturnPoint[];
  // 점을 찍을 날(매수일)
  dots?: string[];
}

interface ReturnLinesChartProps {
  title: string;
  subtitle: string;
  series: ReturnSeries[];
  // multiple: 1 + 수익률 배수(2배 넘게 벌어지면 로그 눈금), linear: 수익률 %
  scale: 'multiple' | 'linear';
  highlightedKey?: string | null;
}

// 항목마다 날짜별 수익률 선(범례를 눌러 켜고 끔, 펼친 항목은 굵게)
export const ReturnLinesChart: React.FC<ReturnLinesChartProps> = ({ title, subtitle, series, scale, highlightedKey }) => {
  const [hidden, setHidden] = useState<Set<string>>(new Set());
  const drawable = useMemo(() => series.filter((s) => s.points.length >= 2), [series]);

  const data = useMemo(() => {
    const byDate = new Map<string, Record<string, number | string>>();
    for (const s of drawable) {
      for (const point of s.points) {
        const row = byDate.get(point.date) ?? { date: point.date };
        row[s.key] = scale === 'multiple' ? 1 + point.pct / 100 : point.pct;
        byDate.set(point.date, row);
      }
    }
    return [...byDate.values()].sort((a, b) => String(a.date).localeCompare(String(b.date)));
  }, [drawable, scale]);

  if (drawable.length === 0) {
    return (
      <ChartCard title={title}>
        <p className="text-sm text-tx-2 py-8 text-center">이 결과에는 일별 값이 없습니다</p>
      </ChartCard>
    );
  }

  const shown = drawable.filter((s) => !hidden.has(s.key));
  const lit = shown.some((s) => s.key === highlightedKey) ? highlightedKey : null;
  const values = data.flatMap((row) => shown.map((s) => row[s.key]).filter((v): v is number => typeof v === 'number'));
  const axis = scale === 'multiple' && values.length > 0 ? multipleAxis(values) : null;
  const toPct = (value: number) => (scale === 'multiple' ? (value - 1) * 100 : value);
  const toggle = (key: string) =>
    setHidden((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });

  return (
    <ChartCard title={title} subtitle={subtitle}>
      <div className="flex flex-wrap gap-2 mb-3" role="group" aria-label="선 켜고 끄기">
        {drawable.map((s) => (
          <button
            key={s.key}
            type="button"
            aria-pressed={!hidden.has(s.key)}
            onClick={() => toggle(s.key)}
            className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-btn border text-xs ${
              hidden.has(s.key) ? 'border-line/50 text-tx-3' : 'border-line-strong text-tx-1'
            }`}
          >
            <span className="w-3 h-0.5" style={{ background: s.color }} aria-hidden />
            {s.name}
          </button>
        ))}
      </div>
      <ResponsiveContainer width="100%" height={300}>
        <LineChart data={data} margin={CHART_MARGIN}>
          <CartesianGrid {...GRID_PROPS} />
          <XAxis dataKey="date" {...AXIS_PROPS} {...dateAxis(data.map((row) => String(row.date)))} />
          {axis ? (
            <YAxis
              {...AXIS_PROPS}
              scale={axis.scale}
              domain={axis.domain as [number, number]}
              ticks={axis.ticks}
              allowDataOverflow={false}
              tickFormatter={multipleLabel}
            />
          ) : (
            <YAxis {...AXIS_PROPS} domain={['auto', 'auto']} tickFormatter={(value: number) => `${Math.round(value)}%`} />
          )}
          <Tooltip {...TOOLTIP_PROPS} formatter={(value: number, name: string) => [formatSigned(toPct(value), formatPercent), name]} />
          {shown.map((s) => (
            <Line
              key={s.key}
              type="monotone"
              dataKey={s.key}
              name={s.name}
              stroke={s.color}
              strokeWidth={lit === s.key ? 3 : 2}
              strokeOpacity={lit && lit !== s.key ? 0.35 : 1}
              dot={false}
              connectNulls
              isAnimationActive={false}
            />
          ))}
          {shown.flatMap((s) =>
            (s.dots ?? []).flatMap((date) => {
              const row = data.find((r) => String(r.date) >= date && typeof r[s.key] === 'number');
              return row
                ? [<ReferenceDot key={`${s.key}-${date}`} x={String(row.date)} y={row[s.key] as number} r={3} fill={s.color} stroke="#fff" strokeWidth={1} />]
                : [];
            }),
          )}
        </LineChart>
      </ResponsiveContainer>
    </ChartCard>
  );
};
