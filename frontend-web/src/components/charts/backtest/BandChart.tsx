import React, { useMemo } from 'react';
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { SignalChartData, SignalPoint, StrategyTransaction } from '../../../services/api';
import { formatUsd } from '../../../utils/format';
import { bandPercent, type BandSetting } from '../../backtest/shared/maBand';
import {
  AXIS_PROPS,
  BAND_COLORS,
  BAND_DASH,
  CHART_HEIGHT,
  CHART_MARGIN,
  GRID_PROPS,
  MARKER_COLORS,
  dateAxis,
  formatUsdTick,
} from './shared/chartStyle';
import { ChartMessage } from './shared/ChartPanel';
import { ChartLegend, type LegendItem } from './shared/ChartLegend';
import { markerDots } from './shared/markers';

interface BandChartProps {
  chart?: SignalChartData | null;
  transactions: StrategyTransaction[];
  // 실행한 요청의 평균 기간 · 위 · 아래 비율(없으면 선 이름이 평균 · 위 선 · 아래 선)
  band?: BandSetting;
}

interface LineNames {
  close: string;
  average: string;
  upper: string;
  lower: string;
}

interface BandTooltipProps {
  active?: boolean;
  payload?: Array<{ payload: SignalPoint }>;
  label?: string;
  names: LineNames;
}

// 날짜 · 수정 종가 · 값이 있는 평균 · 위 · 아래 선 툴팁
const BandTooltip: React.FC<BandTooltipProps> = ({ active, payload, label, names }) => {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload;
  const rows: [string, number | undefined][] = [
    [names.average, point.average],
    [names.upper, point.upperBand],
    [names.lower, point.lowerBand],
  ];
  return (
    <div className="bg-surface p-3 border border-line-strong rounded shadow-lg">
      <p className="font-semibold text-tx-1">{label}</p>
      <p className="text-sm text-tx-1">{`${names.close}: ${formatUsd(point.close)}`}</p>
      {rows.map(([name, value]) =>
        value != null ? <p key={name} className="text-sm text-tx-1">{`${name}: ${formatUsd(value)}`}</p> : null,
      )}
    </div>
  );
};

// 매수(수량 > 0) · 매도(수량 < 0) 거래의 신호 난 날마다 그 날짜 수정 종가(차트에 없는 날은 뺌)
const tradeDots = (points: SignalPoint[], transactions: StrategyTransaction[], sign: 1 | -1) => {
  const closeOn = new Map(points.map((point) => [point.date, point.close]));
  return transactions
    .filter((tx) => Math.sign(tx.shares) === sign)
    .flatMap((tx) => {
      const value = closeOn.get(tx.date);
      return value == null ? [] : [{ date: tx.date, value }];
    });
};

// 밴드 결과의 수정 종가 · 평균 · 위 선 · 아래 선과 매수 · 매도 신호 난 날 점(결과 기간 전체, 값은 응답 그대로)
export const BandChart: React.FC<BandChartProps> = ({ chart, transactions, band }) => {
  const names: LineNames = {
    close: '수정 종가',
    average: band ? `${band.window}일선` : '평균',
    upper: band ? `위 선 +${bandPercent(band.upperRate)}` : '위 선',
    lower: band ? `아래 선 -${bandPercent(band.lowerRate)}` : '아래 선',
  };
  const points = useMemo(() => chart?.points ?? [], [chart]);
  const xAxis = useMemo(() => dateAxis(points.map((point) => point.date)), [points]);
  const buys = useMemo(() => tradeDots(points, transactions, 1), [points, transactions]);
  const sells = useMemo(() => tradeDots(points, transactions, -1), [points, transactions]);

  if (points.length < 2) {
    return <ChartMessage plain>이 결과에는 밴드 값이 없습니다</ChartMessage>;
  }

  const legend: LegendItem[] = [
    { name: names.close, color: BAND_COLORS.close, shape: 'line' },
    { name: names.average, color: BAND_COLORS.average, shape: 'line' },
    { name: names.upper, color: BAND_COLORS.average, shape: 'dashed' },
    { name: names.lower, color: BAND_COLORS.average, shape: 'dashed' },
    { name: '매수 신호', color: MARKER_COLORS.purchase, shape: 'dot' },
    { name: '매도 신호', color: MARKER_COLORS.sale, shape: 'dot' },
  ];
  const line = (dataKey: keyof SignalPoint, name: string, color: string, width: number, dash?: string) => (
    <Line
      type="monotone"
      dataKey={dataKey}
      name={name}
      stroke={color}
      strokeWidth={width}
      strokeDasharray={dash}
      dot={false}
      isAnimationActive={false}
    />
  );

  return (
    <div>
      <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
        <LineChart data={points} margin={CHART_MARGIN}>
          <CartesianGrid {...GRID_PROPS} />
          <XAxis dataKey="date" {...AXIS_PROPS} {...xAxis} />
          <YAxis {...AXIS_PROPS} tickFormatter={formatUsdTick} domain={['auto', 'auto']} />
          <Tooltip content={<BandTooltip names={names} />} />
          {line('close', names.close, BAND_COLORS.close, 1)}
          {line('average', names.average, BAND_COLORS.average, 2)}
          {line('upperBand', names.upper, BAND_COLORS.average, 1.5, BAND_DASH)}
          {line('lowerBand', names.lower, BAND_COLORS.average, 1.5, BAND_DASH)}
          {markerDots(buys, 'value', MARKER_COLORS.purchase, 'buy')}
          {markerDots(sells, 'value', MARKER_COLORS.sale, 'sell')}
        </LineChart>
      </ResponsiveContainer>
      <ChartLegend items={legend} />
    </div>
  );
};
