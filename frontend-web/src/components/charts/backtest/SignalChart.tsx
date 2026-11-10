import React, { useMemo } from 'react';
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { SignalChartData, SignalCross, SignalPoint } from '../../../services/api';
import { formatUsd } from '../../../utils/format';
import {
  AXIS_PROPS,
  CHART_HEIGHT,
  CHART_MARGIN,
  GRID_PROPS,
  SIGNAL_COLORS,
  dateAxis,
  formatUsdTick,
} from './shared/chartStyle';
import { ChartMessage } from './shared/ChartPanel';
import { markerDots } from './shared/markers';

interface SignalChartProps {
  chart?: SignalChartData | null;
  // 실행한 요청의 짧은 · 긴 기간(없으면 선 이름이 짧은 선 · 긴 선)
  windows?: { short: number; long: number };
}

interface LineNames {
  close: string;
  short: string;
  long: string;
}

interface SignalTooltipProps {
  active?: boolean;
  payload?: Array<{ payload: SignalPoint }>;
  label?: string;
  names: LineNames;
}

// 날짜 · 수정 종가 · 값이 있는 평균 툴팁
const SignalTooltip: React.FC<SignalTooltipProps> = ({ active, payload, label, names }) => {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload;
  return (
    <div className="bg-surface p-3 border border-line-strong rounded shadow-lg">
      <p className="font-semibold text-tx-1">{label}</p>
      <p className="text-sm text-tx-1">{`${names.close}: ${formatUsd(point.close)}`}</p>
      {point.shortAverage != null && <p className="text-sm text-tx-1">{`${names.short}: ${formatUsd(point.shortAverage)}`}</p>}
      {point.longAverage != null && <p className="text-sm text-tx-1">{`${names.long}: ${formatUsd(point.longAverage)}`}</p>}
    </div>
  );
};

// 그 종류 교차마다 그 날짜 짧은 선 값(짧은 선 값이 없는 날은 뺌)
const crossDots = (points: SignalPoint[], crosses: SignalCross[], type: SignalCross['type']) => {
  const shortOn = new Map(points.map((point) => [point.date, point.shortAverage]));
  return crosses
    .filter((cross) => cross.type === type)
    .flatMap((cross) => {
      const value = shortOn.get(cross.date);
      return value == null ? [] : [{ date: cross.date, value }];
    });
};

const LINE_SWATCH = 'inline-block w-4 h-0.5';
const DOT_SWATCH = 'inline-block w-2.5 h-2.5 rounded-full';

// 이동평균 결과의 수정 종가 · 짧은 선 · 긴 선과 골든 · 데드 크로스 점(결과 기간 전체, 값은 응답 그대로)
export const SignalChart: React.FC<SignalChartProps> = ({ chart, windows }) => {
  const names: LineNames = {
    close: '수정 종가',
    short: windows ? `${windows.short}일선` : '짧은 선',
    long: windows ? `${windows.long}일선` : '긴 선',
  };
  const points = useMemo(() => chart?.points ?? [], [chart]);
  const xAxis = useMemo(() => dateAxis(points.map((point) => point.date)), [points]);
  const golden = useMemo(() => crossDots(points, chart?.crosses ?? [], 'GOLDEN'), [points, chart]);
  const dead = useMemo(() => crossDots(points, chart?.crosses ?? [], 'DEAD'), [points, chart]);

  if (points.length < 2) {
    return <ChartMessage plain>이 결과에는 이동평균 값이 없습니다</ChartMessage>;
  }

  const legend = [
    { name: names.close, color: SIGNAL_COLORS.close, swatch: LINE_SWATCH },
    { name: names.short, color: SIGNAL_COLORS.short, swatch: LINE_SWATCH },
    { name: names.long, color: SIGNAL_COLORS.long, swatch: LINE_SWATCH },
    { name: '골든 크로스', color: SIGNAL_COLORS.golden, swatch: DOT_SWATCH },
    { name: '데드 크로스', color: SIGNAL_COLORS.dead, swatch: DOT_SWATCH },
  ];

  return (
    <div>
      <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
        <LineChart data={points} margin={CHART_MARGIN}>
          <CartesianGrid {...GRID_PROPS} />
          <XAxis dataKey="date" {...AXIS_PROPS} {...xAxis} />
          <YAxis {...AXIS_PROPS} tickFormatter={formatUsdTick} domain={['auto', 'auto']} />
          <Tooltip content={<SignalTooltip names={names} />} />
          <Line
            type="monotone"
            dataKey="close"
            name={names.close}
            stroke={SIGNAL_COLORS.close}
            strokeWidth={1}
            dot={false}
            isAnimationActive={false}
          />
          <Line
            type="monotone"
            dataKey="shortAverage"
            name={names.short}
            stroke={SIGNAL_COLORS.short}
            strokeWidth={2}
            dot={false}
            isAnimationActive={false}
          />
          <Line
            type="monotone"
            dataKey="longAverage"
            name={names.long}
            stroke={SIGNAL_COLORS.long}
            strokeWidth={2}
            dot={false}
            isAnimationActive={false}
          />
          {markerDots(golden, 'value', SIGNAL_COLORS.golden, 'golden')}
          {markerDots(dead, 'value', SIGNAL_COLORS.dead, 'dead')}
        </LineChart>
      </ResponsiveContainer>
      <ul aria-label="차트 범례" className="mt-2 flex flex-wrap justify-center gap-x-4 gap-y-1 text-xs text-tx-2">
        {legend.map((item) => (
          <li key={item.name} className="flex items-center gap-1.5">
            <span aria-hidden="true" className={item.swatch} style={{ backgroundColor: item.color }} />
            {item.name}
          </li>
        ))}
      </ul>
    </div>
  );
};
