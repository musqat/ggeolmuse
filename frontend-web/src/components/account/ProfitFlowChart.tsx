import React, { useMemo } from 'react';
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ReferenceDot,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { MarketComparisonResponse, MarketPoint, ProfitFlowResponse } from '../../services/api';
import { gainLossClass } from '../../utils/gainLoss';
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
import { signedKrw } from './stats/statsFormat';

const LINE_COLOR = '#f59e0b';
const MARKET_COLOR = 'rgb(var(--text-2))';
const GAIN_COLOR = 'rgb(var(--gain))';
const LOSS_COLOR = 'rgb(var(--loss))';

// 위 · 아래 끝 점의 최고 · 최저 글자 자리(그림 영역 안)
const Y_PADDING = { top: 24, bottom: 24 };

const NO_MARKET: MarketPoint[] = [];

const BELOW_CLASS = 'mt-3 text-sm text-tx-2';

// 범례 글자는 본문 색
const legendText = (value: string) => <span className="text-tx-1">{value}</span>;

// S&P 500 비교 단추 상태와 켰을 때의 시장 비교 쿼리 상태
export interface MarketCompare {
  on: boolean;
  onToggle: () => void;
  status: 'pending' | 'error' | 'success';
  data?: MarketComparisonResponse;
  onRetry: () => void;
}

// 그래프 아래 S&P 500 비교 한 줄(불러오는 중 · 실패 · 시세 없음은 안내)
const MarketSummary: React.FC<{ compare: MarketCompare; profitKrw: number }> = ({ compare, profitKrw }) => {
  if (compare.status === 'pending') {
    return <p className={BELOW_CLASS}>{'S&P 500 시세를 불러오는 중입니다'}</p>;
  }
  if (compare.status === 'error') {
    return (
      <p className={BELOW_CLASS}>
        {'S&P 500 비교를 불러오지 못했습니다 '}
        <button type="button" onClick={compare.onRetry} className="text-tx-1 font-medium underline hover:no-underline">
          다시 시도
        </button>
      </p>
    );
  }
  const market = compare.data;
  if (!market || market.points.length === 0) {
    return <p className={BELOW_CLASS}>{'S&P 500 시세를 불러오지 못했습니다'}</p>;
  }
  return (
    <p className={BELOW_CLASS}>
      {'같은 날짜에 같은 돈을 S&P 500(SPY)에 넣었다면 '}
      <span className={`font-semibold ${gainLossClass(market.currentProfitKrw)}`}>{signedKrw(market.currentProfitKrw)}</span>
      {` (내 계좌보다 ${signedKrw(market.currentProfitKrw - profitKrw)})`}
    </p>
  );
};

// 거래일마다 원화 수익 선과 최고 · 최저 점, 켜면 같은 날짜 SPY 수익 점선
export const ProfitFlowChart: React.FC<{ flow: ProfitFlowResponse; compare?: MarketCompare }> = ({ flow, compare }) => {
  const axis = useMemo(() => dateAxis(flow.points.map((point) => point.date)), [flow.points]);
  const { best, worst } = flow;
  const market = compare?.on && compare.status === 'success' ? (compare.data?.points ?? NO_MARKET) : NO_MARKET;
  const overlay = market.length > 0;
  const data = useMemo(() => {
    if (market.length === 0) return flow.points;
    const byDate = new Map(market.map((point) => [point.date, point.profitKrw]));
    return flow.points.map((point) => ({ ...point, marketKrw: byDate.get(point.date) ?? null }));
  }, [flow.points, market]);

  return (
    <ChartCard>
      <div className="mb-4">
        <div className="flex items-center justify-between gap-3 mb-2">
          <h3 className="text-lg font-semibold text-tx-1">수익 흐름</h3>
          {compare && (
            <button
              type="button"
              aria-pressed={compare.on}
              onClick={compare.onToggle}
              className={`shrink-0 px-3 py-1 text-sm rounded-btn border border-line/50 ${
                compare.on ? 'bg-elevated text-tx-1 font-medium' : 'text-tx-2 hover:text-tx-1'
              }`}
            >
              {'S&P 500 비교'}
            </button>
          )}
        </div>
        <p className="text-sm text-tx-2">거래일마다 평가손익 + 실현 손익 + 받은 배당 − 수수료(그 날짜 환율로 원화)</p>
      </div>
      <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
        <LineChart data={data} margin={CHART_MARGIN}>
          <CartesianGrid {...GRID_PROPS} />
          <XAxis dataKey="date" {...AXIS_PROPS} {...axis} />
          <YAxis {...AXIS_PROPS} tickFormatter={formatKrwTick} width={64} padding={Y_PADDING} />
          <Tooltip
            {...TOOLTIP_PROPS}
            labelFormatter={formatTooltipDate}
            formatter={(value: number, name: string) => [signedKrw(value), name]}
          />
          {overlay && <Legend formatter={legendText} />}
          <ReferenceLine y={0} stroke="rgb(var(--line-strong))" strokeDasharray="4 4" />
          <Line
            type="linear"
            dataKey="profitKrw"
            name={overlay ? '내 계좌' : '수익'}
            stroke={LINE_COLOR}
            strokeWidth={2}
            dot={false}
            legendType="plainline"
          />
          {overlay && (
            <Line
              type="linear"
              dataKey="marketKrw"
              name="S&P 500"
              stroke={MARKET_COLOR}
              strokeWidth={2}
              strokeDasharray="6 4"
              dot={false}
              legendType="plainline"
              connectNulls
            />
          )}
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
      {compare?.on && <MarketSummary compare={compare} profitKrw={flow.currentProfitKrw} />}
    </ChartCard>
  );
};
