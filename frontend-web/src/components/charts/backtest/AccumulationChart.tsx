import React, { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { Line, YAxis, Tooltip } from "recharts";
import { closeOf, fetchSymbolPrices } from './shared/prices';
import {
  AXIS_PROPS,
  dateAxis,
  formatTooltipDate,
  formatUsdTick,
  MARKER_COLORS,
  TOOLTIP_PROPS,
} from './shared/chartStyle';
import { ChartCard, ChartMessage, ChartPanel } from './shared/ChartPanel';
import { markerDots } from './shared/markers';
import { useChartPeriod } from "../common/hooks/useChartPeriod";
import { getApiErrorMessage } from '../../../utils/apiError';
import type { OHLCData } from '../../../types/ohlc';
import { getTodayString } from "../../../utils/dateUtils";

// 서버가 배당으로 산 거래에 다는 trigger
const DIVIDEND_REINVEST = "배당 재투자";

interface Transaction {
  date: string;
  actualDate?: string;
  shares: number;
  trigger?: string;
}

interface StrategyBacktestChartProps {
  symbol: string;
  transactions: Transaction[];
  startDate: string;
  // 평가일(없으면 오늘까지)
  endDate?: string;
}

interface ChartDataPoint {
  date: string;
  stockPrice: number;
  // 이 거래일에 일반 매수 · 배당 재투자가 있었나
  isPurchase: boolean;
  isDividendReinvest: boolean;
}

const EMPTY_CHART_DATA: ChartDataPoint[] = [];

// 적립식 · 조건부 결과의 주가 추이(매수 · 배당 재투자 점)
export const AccumulationChart: React.FC<StrategyBacktestChartProps> = ({
  symbol,
  transactions,
  startDate,
  endDate,
}) => {
  const period = useChartPeriod("purchase");
  const { chartPeriod, customStartDate, getStartDateFromPeriod } = period;

  const firstPurchaseDate = transactions[0]?.actualDate || transactions[0]?.date || startDate;

  const chartStartDate = useMemo(
    () => getStartDateFromPeriod(chartPeriod, firstPurchaseDate, customStartDate),
    [chartPeriod, customStartDate, firstPurchaseDate, getStartDateFromPeriod],
  );

  const { data: priceData, isLoading: loading, error } = useQuery({
    queryKey: ["backtest", "chart", "accumulation", symbol, chartStartDate, endDate],
    enabled: Boolean(symbol) && transactions.length > 0,
    queryFn: () => fetchSymbolPrices<OHLCData>(symbol, chartStartDate, endDate || getTodayString()),
  });
  const errorMessage = error ? getApiErrorMessage(error, "차트 데이터를 불러오는데 실패했습니다") : null;

  const chartData = useMemo(() => {
    if (!priceData) return EMPTY_CHART_DATA;
    const availableDates = priceData.map((item) => item.date).sort();

    // 거래를 실제 거래일(주말 · 휴일이면 다음 영업일)에 맞추고 일반 매수 · 배당 재투자를 따로 셈(고른 기간보다 앞선 거래는 뺌)
    const purchaseDates = new Set<string>();
    const dividendDates = new Set<string>();
    transactions.forEach((tx) => {
      const txDate = tx.actualDate || tx.date;
      if (txDate < chartStartDate) return;
      const tradingDate = availableDates.find((d) => d >= txDate);
      if (!tradingDate) return;
      if (tx.trigger === DIVIDEND_REINVEST) dividendDates.add(tradingDate);
      else purchaseDates.add(tradingDate);
    });

    return priceData.map((item) => ({
      date: item.date,
      stockPrice: closeOf(item),
      isPurchase: purchaseDates.has(item.date),
      isDividendReinvest: dividendDates.has(item.date),
    }));
  }, [priceData, transactions, chartStartDate]);

  const xAxis = dateAxis(chartData.map((d) => d.date));

  // Y축 범위(±50 여유)
  const priceRange = useMemo(() => {
    if (chartData.length === 0) return [0, 100];
    const prices = chartData.map((d) => d.stockPrice);
    return [Math.max(0, Math.min(...prices) - 50), Math.max(...prices) + 50];
  }, [chartData]);

  const purchasePoints = useMemo(() => chartData.filter((d) => d.isPurchase), [chartData]);
  const dividendReinvestPoints = useMemo(() => chartData.filter((d) => d.isDividendReinvest), [chartData]);

  if (loading) {
    return <ChartMessage>차트 데이터를 불러오는 중...</ChartMessage>;
  }

  if (errorMessage) {
    return <ChartMessage tone="error">{errorMessage}</ChartMessage>;
  }

  if (chartData.length === 0) {
    return <ChartMessage>차트 데이터가 없습니다.</ChartMessage>;
  }

  const bought = transactions.filter((t) => t.trigger !== DIVIDEND_REINVEST).length;
  const totalShares = transactions.reduce((sum, t) => sum + t.shares, 0);

  return (
    <ChartCard
      title={`${symbol} 주가 추이`}
      subtitle={`${bought}회 매수 • 총 ${totalShares.toFixed(2)}주 보유`}
      period={period}
      plain
    >
      <ChartPanel data={chartData} xAxis={xAxis}>
        <YAxis {...AXIS_PROPS} tickFormatter={formatUsdTick} domain={priceRange} allowDecimals={false} />
        <Tooltip
          {...TOOLTIP_PROPS}
          formatter={(value: number | string, name: string) =>
            name === "주가" ? [`$${Number(value).toFixed(2)}`, name] : [value, name]
          }
          labelFormatter={formatTooltipDate}
        />
        <Line
          type="monotone"
          dataKey="stockPrice"
          stroke="#3b82f6"
          strokeWidth={2}
          dot={false}
          name="주가"
          isAnimationActive={false}
        />
        {markerDots(purchasePoints, "stockPrice", MARKER_COLORS.purchase, "purchase")}
        {markerDots(dividendReinvestPoints, "stockPrice", MARKER_COLORS.dividend, "dividend")}
      </ChartPanel>
      <p className="text-xs text-tx-2 mt-2 text-center">
        파란 선 = 주가 | 보라 점 = 매수({purchasePoints.length}개)
        {dividendReinvestPoints.length > 0 && ` | 녹색 점 = 배당 재투자(${dividendReinvestPoints.length}개)`}
      </p>
    </ChartCard>
  );
};
