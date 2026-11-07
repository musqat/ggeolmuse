import React, { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Line, YAxis, Tooltip, ReferenceDot } from 'recharts';
import type { ComparisonDetail } from '../../../services/api';
import { closeOf, fetchSymbolPrices } from './shared/prices';
import { AXIS_PROPS, dateAxis, formatTooltipDate, formatUsdTick, MARKER_PROPS, TOOLTIP_PROPS } from './shared/chartStyle';
import { ChartCard, ChartMessage, ChartPanel } from './shared/ChartPanel';
import type { OHLCData } from '../../../types/ohlc';
import { useChartPeriod } from '../common/hooks/useChartPeriod';
import { CHART_COLORS } from '../common/constants';
import { getTodayString } from '../../../utils/dateUtils';
import { DIVIDEND_REINVEST, isSale } from '@components/backtest/results/resultValues';

interface StrategyItem {
  name: string;
  additionalData?: ComparisonDetail | null;
}

interface StockPriceWithStrategyChartProps {
  strategies: StrategyItem[];
  strategyNames: Record<string, string>;
}

interface PricePoint {
  date: string;
  stockPrice: number;
}

const EMPTY_PRICES: OHLCData[] = [];

// 전략의 매수일(단순은 매수일 하나, 적립식 · 조건부는 배당 재투자 · 매도를 뺀 거래일)
const buyDates = (data: ComparisonDetail): string[] =>
  data.transactions
    ? data.transactions
        .filter((tx) => tx.trigger !== DIVIDEND_REINVEST && !isSale(tx))
        .map((tx) => tx.actualDate || tx.date)
    : data.purchaseDate
      ? [data.purchaseDate]
      : [];

// 전략 비교의 주가 추이(전략마다 색을 달리한 매수 점)
export const CompareStrategiesChart: React.FC<StockPriceWithStrategyChartProps> = ({ strategies, strategyNames }) => {
  const period = useChartPeriod('purchase');
  const { chartPeriod, customStartDate, getStartDateFromPeriod } = period;

  const first = strategies[0]?.additionalData;
  const symbol = first?.symbol;
  const originalStartDate = first?.startDate || first?.purchaseDate;
  const endDate = first?.endDate || first?.currentDate;
  const chartStart = originalStartDate ? getStartDateFromPeriod(chartPeriod, originalStartDate, customStartDate) : undefined;

  const { data: prices = EMPTY_PRICES, isLoading: loading } = useQuery({
    queryKey: ['backtest', 'chart', 'strategies', symbol, chartStart, endDate],
    enabled: Boolean(symbol && chartStart),
    queryFn: () => fetchSymbolPrices<OHLCData>(symbol!, chartStart!, endDate || getTodayString()).catch(() => EMPTY_PRICES),
  });

  const chartData = useMemo<PricePoint[]>(
    () => prices.map((row) => ({ date: row.date, stockPrice: closeOf(row) })),
    [prices],
  );

  // 고른 기간 안의 매수일(휴장일이면 다음 거래일)
  const purchasePoints = useMemo(
    () =>
      strategies.flatMap((strategy, index) =>
        buyDates(strategy.additionalData ?? {}).flatMap((date) => {
          if (!chartStart || date < chartStart) return [];
          const point = chartData.find((d) => d.date >= date);
          return point ? [{ ...point, color: CHART_COLORS[index % CHART_COLORS.length] }] : [];
        }),
      ),
    [strategies, chartData, chartStart],
  );

  if (loading) {
    return <ChartMessage>차트 데이터를 불러오는 중...</ChartMessage>;
  }

  if (chartData.length === 0) {
    return <ChartMessage>차트 데이터가 없습니다.</ChartMessage>;
  }

  return (
    <ChartCard title={`${symbol} 주가(달러)`} period={period} plain>
      <ChartPanel data={chartData} xAxis={dateAxis(chartData.map((d) => d.date))}>
        <YAxis {...AXIS_PROPS} tickFormatter={formatUsdTick} />
        <Tooltip
          {...TOOLTIP_PROPS}
          formatter={(value: number | string) => [`$${Number(value).toFixed(2)}`, '주가']}
          labelFormatter={formatTooltipDate}
        />
        <Line
          type="monotone"
          dataKey="stockPrice"
          stroke="rgb(var(--text-2))"
          strokeWidth={2}
          dot={false}
          name="주가"
          isAnimationActive={false}
        />
        {purchasePoints.map((point, index) => (
          <ReferenceDot key={`purchase-${index}`} x={point.date} y={point.stockPrice} {...MARKER_PROPS} fill={point.color} />
        ))}
      </ChartPanel>
      <div className="flex flex-wrap justify-center gap-3 text-xs text-tx-2 mt-2">
        <span>매수 점:</span>
        {strategies.map((s, index) => (
          <span key={s.name} className="inline-flex items-center gap-1">
            <span className="w-2.5 h-2.5 rounded-full" style={{ background: CHART_COLORS[index % CHART_COLORS.length] }} aria-hidden />
            <span>{strategyNames[s.name] || s.name}</span>
          </span>
        ))}
      </div>
    </ChartCard>
  );
};
