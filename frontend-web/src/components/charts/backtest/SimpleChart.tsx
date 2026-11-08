import React, { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { Line, YAxis, Tooltip, ReferenceDot } from "recharts";
import { closeOf, fetchSymbolPrices } from './shared/prices';
import {
  AXIS_PROPS,
  dateAxis,
  formatUsdTick,
  MARKER_COLORS,
  MARKER_LABEL_STYLE,
  MARKER_PROPS,
  OPTIMAL_MARKER_PROPS,
} from './shared/chartStyle';
import { ChartCard, ChartMessage, ChartPanel } from './shared/ChartPanel';
import { markerDots } from './shared/markers';
import { useChartPeriod } from "../common/hooks/useChartPeriod";
import { getTodayString } from "../../../utils/dateUtils";

// 서버 date 는 "2025-01-08" 문자열 또는 [2025, 1, 8] 배열
type RawOhlcDate = string | [number, number, number];

interface RawOhlc {
  symbol: string;
  date: RawOhlcDate;
  closePrice?: number;
  adjustedClose?: number;
}

// [2025, 1, 8] → "2025-01-08"
const toDateString = (date: RawOhlcDate): string =>
  Array.isArray(date)
    ? `${date[0]}-${String(date[1]).padStart(2, "0")}-${String(date[2]).padStart(2, "0")}`
    : date;

interface SimpleBacktestChartProps {
  symbol: string;
  purchaseDate: string;
  optimalBuyDate?: string;
  optimalSellDate?: string;
  dividendReinvestDates?: string[];
  // 평가일(매도일, 없으면 오늘까지)
  endDate?: string;
  // 툴팁에서 주가 아래 덧붙일 날짜별 줄
  tooltipExtra?: (date: string) => React.ReactNode;
}

interface PricePoint {
  date: string;
  price: number;
}

interface PriceTooltipProps {
  active?: boolean;
  payload?: Array<{ payload: PricePoint }>;
  label?: string;
  purchaseDate?: string;
  extra?: (date: string) => React.ReactNode;
}

// 주가 툴팁(날짜 · 매수 시점 · 주가 · 덧붙일 줄, payload 모양은 차트 데이터를 따름)
export const PriceTooltip: React.FC<PriceTooltipProps> = ({ active, payload, label, purchaseDate, extra }) => {
  if (!active || !payload?.length) return null;
  const data = payload[0].payload;
  return (
    <div className="bg-surface p-3 border border-line-strong rounded shadow-lg">
      <p className="font-semibold text-tx-1">{label}</p>
      {data.date === purchaseDate && <p className="text-xs text-brand mb-1">매수 시점</p>}
      <p className="text-sm text-tx-1">주가: ${data.price?.toFixed(2) || "-"}</p>
      {extra?.(data.date)}
    </div>
  );
};

// 날짜의 점(휴장일이면 다음 거래일, 고른 기간보다 앞서면 없음)
const pointOn = (data: PricePoint[], date: string | undefined, chartStart: string) =>
  date && date >= chartStart ? data.find((d) => d.date >= date) ?? null : null;

// 단순 매수 결과의 주가 추이(매수 · 배당 재투자 · 최적 매수 · 매도 점)
export const SimpleChart: React.FC<SimpleBacktestChartProps> = ({
  symbol,
  purchaseDate,
  optimalBuyDate,
  optimalSellDate,
  dividendReinvestDates,
  endDate,
  tooltipExtra,
}) => {
  const period = useChartPeriod("purchase");
  const { chartPeriod, customStartDate, getStartDateFromPeriod } = period;

  const chartStart = getStartDateFromPeriod(chartPeriod, purchaseDate, customStartDate);
  const { data: priceData = [], isLoading: loading } = useQuery({
    queryKey: ["backtest", "chart", "simple", symbol, purchaseDate, endDate, chartPeriod, customStartDate],
    queryFn: () => fetchSymbolPrices<RawOhlc>(symbol, chartStart, endDate || getTodayString()),
    staleTime: 5 * 60 * 1000,
  });

  const chartData = useMemo<PricePoint[]>(
    () => priceData.map((candle) => ({ date: toDateString(candle.date), price: closeOf(candle) })),
    [priceData],
  );

  const xAxis = dateAxis(chartData.map((d) => d.date));
  const purchasePoint = useMemo(() => pointOn(chartData, purchaseDate, chartStart), [chartData, purchaseDate, chartStart]);
  const optimalBuyPoint = useMemo(() => (optimalBuyDate ? chartData.find((d) => d.date === optimalBuyDate) ?? null : null), [chartData, optimalBuyDate]);
  const optimalSellPoint = useMemo(() => (optimalSellDate ? chartData.find((d) => d.date === optimalSellDate) ?? null : null), [chartData, optimalSellDate]);
  const dividendReinvestPoints = useMemo(
    () =>
      (dividendReinvestDates ?? [])
        .map((date) => pointOn(chartData, date, chartStart))
        .filter((p): p is PricePoint => p !== null),
    [chartData, dividendReinvestDates, chartStart],
  );

  // Y축 범위(고점 · 저점에서 ±50)
  const priceRange = useMemo(() => {
    if (chartData.length === 0) return [0, 100];
    const prices = chartData.map((d) => d.price);
    return [Math.max(0, Math.min(...prices) - 50), Math.max(...prices) + 50];
  }, [chartData]);

  if (loading) {
    return <ChartMessage plain>차트 데이터를 불러오는 중...</ChartMessage>;
  }

  if (chartData.length === 0) {
    return <ChartMessage plain>차트 데이터가 없습니다.</ChartMessage>;
  }

  return (
    <ChartCard period={period} plain>
      <ChartPanel data={chartData} xAxis={xAxis}>
        <YAxis {...AXIS_PROPS} tickFormatter={formatUsdTick} domain={priceRange} allowDecimals={false} />
        <Tooltip content={<PriceTooltip purchaseDate={purchasePoint?.date} extra={tooltipExtra} />} />
        <Line
          type="monotone"
          dataKey="price"
          name={`${symbol} 주가`}
          stroke="#3b82f6"
          strokeWidth={2}
          dot={false}
          isAnimationActive={false}
        />
        {purchasePoint && (
          <ReferenceDot
            x={purchasePoint.date}
            y={purchasePoint.price}
            {...MARKER_PROPS}
            r={6}
            fill={MARKER_COLORS.purchase}
            label={{ value: "매수", position: "top", ...MARKER_LABEL_STYLE }}
          />
        )}
        {optimalBuyPoint && (
          <ReferenceDot
            x={optimalBuyPoint.date}
            y={optimalBuyPoint.price}
            {...OPTIMAL_MARKER_PROPS}
            r={6}
            label={{ value: "최적 매수", position: "bottom", ...MARKER_LABEL_STYLE }}
          />
        )}
        {optimalSellPoint && (
          <ReferenceDot
            x={optimalSellPoint.date}
            y={optimalSellPoint.price}
            {...OPTIMAL_MARKER_PROPS}
            r={6}
            label={{ value: "최적 매도", position: "top", ...MARKER_LABEL_STYLE }}
          />
        )}
        {markerDots(dividendReinvestPoints, "price", MARKER_COLORS.dividend, "dividend")}
      </ChartPanel>
      <p className="text-xs text-tx-2 mt-2 text-center">
        파란 선 = 주가 | 보라 점 = 매수 | 녹색 점 = 배당 재투자 | 금색 점 = 최적 매수 · 매도(원화 기준)
        {tooltipExtra && ' | 선에 마우스를 올리면 보유 수익률 · 여기서 샀다면 평가일까지 수익률'}
      </p>
    </ChartCard>
  );
};
