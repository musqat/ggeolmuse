import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Line, ReferenceDot, YAxis, Tooltip } from 'recharts';
import { closeOf, fetchSymbolPrices } from './shared/prices';
import {
  AXIS_PROPS,
  dateAxis,
  formatTooltipDate,
  formatUsdTick,
  MARKER_COLORS,
  OPTIMAL_MARKER_PROPS,
  TOOLTIP_PROPS,
} from './shared/chartStyle';
import { ChartCard, ChartMessage, ChartPanel } from './shared/ChartPanel';
import { markerDots } from './shared/markers';
import { useChartPeriod } from '../common/hooks/useChartPeriod';
import type { OHLCData } from '../../../types/ohlc';
import { getTodayString } from '../../../utils/dateUtils';
import { formatUsd } from '../../../utils/format';

interface SymbolData {
  symbol: string;
  purchaseDate: string;
  color: string; // 선 색
  optimalBuyDate?: string | null;
  optimalSellDate?: string | null;
}

interface SymbolComparisonChartProps {
  symbols: SymbolData[];
  startDate: string;
  endDate?: string;
}

interface ChartDataPoint {
  date: string;
  [key: string]: string | number; // 종목마다 `${symbol}_price`
}

const EMPTY_CHART_DATA: ChartDataPoint[] = [];
const DEFAULT_PRICE_DOMAIN: [number, number] = [0, 100];

// 종목 비교의 주가 추이(매수 · 최적 매수 · 매도 점)
export const CompareSymbolsChart: React.FC<SymbolComparisonChartProps> = ({ symbols, startDate, endDate }) => {
  const period = useChartPeriod('purchase');
  const { chartPeriod, customStartDate, getStartDateFromPeriod } = period;

  const chartStart = getStartDateFromPeriod(chartPeriod, startDate, customStartDate);
  const lastDate = endDate || getTodayString();

  const { data, isLoading: loading, error } = useQuery({
    queryKey: ['backtest', 'chart', 'symbols', symbols.map((s) => s.symbol), chartStart, lastDate],
    enabled: symbols.length > 0,
    queryFn: async () => {
      const results = await Promise.all(
        symbols.map(({ symbol }) =>
          fetchSymbolPrices<OHLCData>(symbol, chartStart, lastDate)
            .then((rows) => ({ symbol, rows }))
            .catch(() => ({ symbol, rows: [] as OHLCData[] })),
        ),
      );

      const byDate = new Map<string, ChartDataPoint>();
      let min = Infinity;
      let max = -Infinity;
      for (const { symbol, rows } of results) {
        for (const row of rows) {
          const point = byDate.get(row.date) ?? { date: row.date };
          const price = closeOf(row);
          point[`${symbol}_price`] = price;
          byDate.set(row.date, point);
          if (price > 0) {
            min = Math.min(min, price);
            max = Math.max(max, price);
          }
        }
      }
      const chartData = [...byDate.values()].sort((a, b) => a.date.localeCompare(b.date));
      const margin = (max - min) * 0.05;
      const priceDomain: [number, number] = chartData.length
        ? [Math.floor(min - margin), Math.ceil(max + margin)]
        : DEFAULT_PRICE_DOMAIN;
      return { chartData, priceDomain };
    },
  });

  const chartData = data?.chartData ?? EMPTY_CHART_DATA;
  const xAxis = dateAxis(chartData.map((d) => d.date));

  // 고른 기간 안의 매수일(휴장일이면 다음 거래일)
  const purchasePoints = symbols.flatMap(({ symbol, purchaseDate }) => {
    if (purchaseDate < chartStart) return [];
    const point = chartData.find((d) => d.date >= purchaseDate && d[`${symbol}_price`] != null);
    return point ? [{ date: point.date, price: point[`${symbol}_price`] as number }] : [];
  });

  if (loading) {
    return <ChartMessage>차트 데이터 로딩 중...</ChartMessage>;
  }

  if (error) {
    return <ChartMessage tone="error">차트 데이터를 가져오는데 실패했습니다.</ChartMessage>;
  }

  if (chartData.length === 0) {
    return <ChartMessage>차트 데이터가 없습니다.</ChartMessage>;
  }

  return (
    <ChartCard title="종목별 주가(달러)" period={period} plain>
      <ChartPanel data={chartData} xAxis={xAxis}>
        <YAxis domain={data?.priceDomain ?? DEFAULT_PRICE_DOMAIN} {...AXIS_PROPS} tickFormatter={formatUsdTick} />
        <Tooltip
          {...TOOLTIP_PROPS}
          formatter={(value: number | string) => formatUsd(Number(value))}
          labelFormatter={formatTooltipDate}
        />
        {symbols.map(({ symbol, color }) => (
          <Line
            key={symbol}
            type="monotone"
            dataKey={`${symbol}_price`}
            stroke={color}
            strokeWidth={2}
            dot={false}
            name={`${symbol} 주가`}
            connectNulls
            isAnimationActive={false}
          />
        ))}
        {markerDots(purchasePoints, 'price', MARKER_COLORS.purchase, 'purchase')}
        {symbols.flatMap(({ symbol, optimalBuyDate, optimalSellDate }) =>
          [optimalBuyDate, optimalSellDate].map((date, i) => {
            const point = date ? chartData.find((d) => d.date === date) : undefined;
            const price = point?.[`${symbol}_price`];
            if (!point || price == null) return null;
            return (
              <ReferenceDot
                key={`${symbol}-optimal-${i === 0 ? 'buy' : 'sell'}`}
                x={point.date}
                y={price as number}
                {...OPTIMAL_MARKER_PROPS}
              />
            );
          }),
        )}
      </ChartPanel>
      <p className="text-xs text-tx-2 mt-2 text-center">
        보라 점 = 매수 | 금색 점 = 최적 매수 · 매도(원화로 가장 싸게 사서 가장 비싸게 파는 날)
      </p>
    </ChartCard>
  );
};
