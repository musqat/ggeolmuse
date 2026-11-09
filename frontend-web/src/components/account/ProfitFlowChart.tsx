import React, { useMemo } from 'react';
import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceDot,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { ProfitFlowResponse } from '../../services/api';
import { formatKrw, formatSigned } from '../../utils/format';
import { ChartCard } from '@components/charts/backtest/shared/ChartPanel';
import {
  AXIS_PROPS,
  CHART_HEIGHT,
  CHART_MARGIN,
  GRID_PROPS,
  MARKER_LABEL_STYLE,
  MARKER_PROPS,
  TOOLTIP_PROPS,
  dateAxis,
  formatKrwTick,
  formatTooltipDate,
} from '@components/charts/backtest/shared/chartStyle';

const LINE_COLOR = '#f59e0b';
const GAIN_COLOR = 'rgb(var(--gain))';
const LOSS_COLOR = 'rgb(var(--loss))';

// 위 · 아래 끝 점의 최고 · 최저 글자 자리
const MARGIN = { ...CHART_MARGIN, top: 24, bottom: 16 };

const signedKrw = (value: number) => formatSigned(Math.round(value), formatKrw);

// 거래일마다 원화 수익 선과 최고 · 최저 점
export const ProfitFlowChart: React.FC<{ flow: ProfitFlowResponse }> = ({ flow }) => {
  const axis = useMemo(() => dateAxis(flow.points.map((point) => point.date)), [flow.points]);
  const { best, worst } = flow;

  return (
    <ChartCard title="수익 흐름" subtitle="거래일마다 평가손익 + 실현 손익 + 받은 배당 − 수수료(그 날짜 환율로 원화)">
      <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
        <LineChart data={flow.points} margin={MARGIN}>
          <CartesianGrid {...GRID_PROPS} />
          <XAxis dataKey="date" {...AXIS_PROPS} {...axis} />
          <YAxis {...AXIS_PROPS} tickFormatter={formatKrwTick} width={64} />
          <Tooltip
            {...TOOLTIP_PROPS}
            labelFormatter={formatTooltipDate}
            formatter={(value: number) => [signedKrw(value), '수익']}
          />
          <ReferenceLine y={0} stroke="rgb(var(--line-strong))" strokeDasharray="4 4" />
          <Line type="linear" dataKey="profitKrw" name="수익" stroke={LINE_COLOR} strokeWidth={2} dot={false} />
          {best && (
            <ReferenceDot
              x={best.date}
              y={best.profitKrw}
              {...MARKER_PROPS}
              fill={GAIN_COLOR}
              label={{ value: '최고', position: 'top', ...MARKER_LABEL_STYLE }}
            />
          )}
          {worst && (
            <ReferenceDot
              x={worst.date}
              y={worst.profitKrw}
              {...MARKER_PROPS}
              fill={LOSS_COLOR}
              label={{ value: '최저', position: 'bottom', ...MARKER_LABEL_STYLE }}
            />
          )}
        </LineChart>
      </ResponsiveContainer>
    </ChartCard>
  );
};
