import React, { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  ReferenceDot,
} from "recharts";
import { DEFAULT_FX_RATE, fetchFxRates } from './shared/fxRates';
import { fetchSymbolPrices } from './shared/prices';
import {
  AXIS_PROPS,
  CHART_HEIGHT,
  CHART_MARGIN,
  formatDayTick,
  formatManwonTick,
  formatUsdTick,
  GRID_PROPS,
  MARKER_COLORS,
  MARKER_LABEL_STYLE,
  MARKER_PROPS,
  OPTIMAL_MARKER_PROPS,
} from './shared/chartStyle';
import { useChartPeriod } from "../common/hooks/useChartPeriod";
import { ChartPeriodSelector } from "../common/components/ChartPeriodSelector";
import { getTodayString } from "../../../utils/dateUtils";

// 가격·환율·평가액은 받지 않는다. 차트가 일자별 캔들에서 직접 구한다.
// 최적 매수·매도도 날짜만 받아 그 날의 데이터를 chartData 에서 찾는다.
// 서버가 date 를 "2025-01-08" 로도, [2025, 1, 8] 배열로도 준다.
type RawOhlcDate = string | [number, number, number];

interface RawOhlc {
  symbol: string;
  date: RawOhlcDate;
  closePrice?: number;
  adjustedClose?: number;
  close?: number;
}

// [2025, 1, 8] → "2025-01-08"
const toDateString = (date: RawOhlcDate): string =>
  Array.isArray(date)
    ? `${date[0]}-${String(date[1]).padStart(2, "0")}-${String(date[2]).padStart(2, "0")}`
    : date;

// 위 응답에 일자별 환율을 얹은 것
type OhlcWithFx = RawOhlc & { fxRate: number };

interface SimpleBacktestChartProps {
  symbol: string;
  purchaseDate: string;
  shares: number;
  investmentAmount: number;
  optimalBuyDate?: string;
  optimalSellDate?: string;
  dividendReinvestDates?: string[];
  // 평가일(매도일). 없으면 오늘까지 그린다
  endDate?: string;
}

export const SimpleChart: React.FC<SimpleBacktestChartProps> = ({
  symbol,
  purchaseDate,
  shares,
  investmentAmount,
  optimalBuyDate,
  optimalSellDate,
  dividendReinvestDates,
  endDate,
}) => {
  // 공통 차트 기간 훅 사용
  const {
    chartPeriod,
    customStartDate,
    setChartPeriod,
    setCustomStartDate,
    getStartDateFromPeriod,
  } = useChartPeriod("purchase");

  // 차트 기간에 따른 시작일 계산
  const getChartStartDate = () => {
    // 사용자가 선택한 기간 그대로 사용 (매수일 이전 데이터도 표시)
    return getStartDateFromPeriod(chartPeriod, purchaseDate, customStartDate);
  };

  // React Query: 차트 데이터 조회 (OHLC + 환율)
  const { data: priceData = [], isLoading: loading } = useQuery({
    queryKey: [
      "backtest",
      "chart",
      "simple",
      symbol,
      purchaseDate,
      endDate,
      chartPeriod,
      customStartDate,
    ],
    queryFn: async () => {
      const ohlcData = await fetchSymbolPrices<RawOhlc>(symbol, getChartStartDate(), endDate || getTodayString());
      if (ohlcData.length === 0) return [];

      // 일자별 환율을 얹는다
      const fxRateMap = await fetchFxRates(ohlcData.map((item) => toDateString(item.date)));
      return ohlcData.map((item) => ({
        ...item,
        fxRate: fxRateMap.get(toDateString(item.date)) || DEFAULT_FX_RATE,
      }));
    },
    staleTime: 5 * 60 * 1000, // 5분
  });

  // 차트 데이터 생성
  const chartData = useMemo(() => {
    if (!priceData || priceData.length === 0) {
      return [];
    }

    const result = priceData.map((candle: OhlcWithFx) => {
      // OHLCPriceDto: adjustedClose (배당/주식분할 반영된 보정 종가)
      // 0 이면 다음 값으로 넘어간다. 주가 0 은 유효한 값이 아니라 원래 동작을 유지한다.
      const dailyPrice =
        candle.adjustedClose || candle.closePrice || candle.close || 0;

      const dateStr = toDateString(candle.date);

      // 해당 날짜의 환율 사용 (각 날짜마다 다른 환율 적용)
      const historicalFxRate = candle.fxRate || DEFAULT_FX_RATE;

      // 매수일에는 투자금을 그대로 사용 (환율 괴리 방지)
      let portfolioValue;
      if (dateStr === purchaseDate) {
        portfolioValue = investmentAmount;
      } else {
        portfolioValue = shares * dailyPrice * historicalFxRate;
      }

      return {
        date: dateStr,
        price: dailyPrice,
        투자금: investmentAmount,
        평가금액: Math.round(portfolioValue),
      };
    });

    return result;
  }, [priceData, shares, investmentAmount, purchaseDate]);

  // 매수 시점 마커 - chartData에서 매수일 찾기 또는 다음 영업일 찾기
  const purchasePoint = useMemo(() => {
    if (chartData.length === 0) return null;

    // 먼저 chartData에서 정확한 날짜 찾기
    let purchaseDateData = chartData.find((d) => d.date === purchaseDate);

    // chartData에 없으면 (주말/휴일), 다음 영업일 찾기
    if (!purchaseDateData) {
      // purchaseDate 이후의 첫 번째 데이터 포인트 (다음 영업일)
      purchaseDateData = chartData.find((d) => d.date > purchaseDate);
    }

    return purchaseDateData || null;
  }, [chartData, purchaseDate]);

  // 최적 매수 시점 마커
  const optimalBuyPoint = useMemo(() => {
    if (chartData.length === 0 || !optimalBuyDate) return null;
    const optimalBuyData = chartData.find((d) => d.date === optimalBuyDate);
    return optimalBuyData || null;
  }, [chartData, optimalBuyDate]);

  // 최적 매도 시점 마커
  const optimalSellPoint = useMemo(() => {
    if (chartData.length === 0 || !optimalSellDate) return null;
    const optimalSellData = chartData.find((d) => d.date === optimalSellDate);
    return optimalSellData || null;
  }, [chartData, optimalSellDate]);

  // 배당 재투자 시점 마커들
  const dividendReinvestPoints = useMemo(() => {
    if (
      chartData.length === 0 ||
      !dividendReinvestDates ||
      dividendReinvestDates.length === 0
    )
      return [];

    return dividendReinvestDates
      .map((date) => {
        // 먼저 chartData에서 정확한 날짜 찾기
        let reinvestDateData = chartData.find((d) => d.date === date);
        // chartData에 없으면 (주말/휴일), 다음 영업일 찾기
        if (!reinvestDateData) {
          reinvestDateData = chartData.find((d) => d.date > date);
        }
        return reinvestDateData;
      })
      .filter((point) => point !== undefined);
  }, [chartData, dividendReinvestDates]);

  // Y축 범위 계산 (고점/저점에서 ±50 padding)
  const priceRange = useMemo(() => {
    if (chartData.length === 0) return [0, 100];

    const prices = chartData.map((d) => d.price);
    const minPrice = Math.min(...prices);
    const maxPrice = Math.max(...prices);

    return [Math.max(0, minPrice - 50), maxPrice + 50];
  }, [chartData]);

  // 커스텀 툴팁
  // recharts 가 넘기는 값이라 payload 모양은 차트 데이터에 달려 있다.
  const CustomTooltip = ({ active, payload, label }: {
    active?: boolean;
    payload?: Array<{ payload: { date: string; price: number; 투자금: number; 평가금액: number } }>;
    label?: string;
  }) => {
    if (active && payload && payload.length) {
      const data = payload[0].payload;
      const isPurchaseDate = data.date === purchaseDate;

      return (
        <div className="bg-surface p-3 border border-line-strong rounded shadow-lg">
          <p className="font-semibold text-tx-1">{label}</p>
          {isPurchaseDate && (
            <p className="text-xs text-brand mb-1">매수 시점</p>
          )}
          {/* data.price가 있으면 직접 표시, 없으면 payload에서 찾기 */}
          <p className="text-sm text-tx-1">
            주가: ${data.price?.toFixed(2) || "-"}
          </p>
          <p className="text-sm text-gain">
            투자금: ₩{data.투자금?.toLocaleString()}
          </p>
          <p className="text-sm text-info">
            평가금액: ₩{data.평가금액?.toLocaleString()}
          </p>
          {data.평가금액 && data.투자금 && (
            <p
              className={`text-sm font-semibold ${data.평가금액 >= data.투자금 ? "text-gain" : "text-loss"}`}
            >
              {data.평가금액 >= data.투자금 ? "수익" : "손실"}: ₩
              {Math.abs(data.평가금액 - data.투자금).toLocaleString()}(
              {((data.평가금액 / data.투자금 - 1) * 100).toFixed(2)}%)
            </p>
          )}
        </div>
      );
    }
    return null;
  };

  if (loading) {
    return (
      <div className="text-center py-8 text-tx-2">
        차트 데이터를 불러오는 중...
      </div>
    );
  }

  if (!chartData || chartData.length === 0) {
    return (
      <div className="text-center py-8 text-tx-2">차트 데이터가 없습니다.</div>
    );
  }

  return (
    <div className="space-y-6 mt-6">
      {/* 주가 차트 */}
      <div className="bg-surface rounded-lg border border-line p-4">
        <h3 className="text-lg font-semibold text-tx-1 mb-4">
          {symbol} 주가 추이
        </h3>
        <ChartPeriodSelector
          chartPeriod={chartPeriod}
          customStartDate={customStartDate}
          onPeriodChange={setChartPeriod}
          onCustomDateChange={setCustomStartDate}
        />
        <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
          <LineChart
            data={chartData}
            margin={CHART_MARGIN}
          >
            <CartesianGrid {...GRID_PROPS} />
            <XAxis
              dataKey="date"
              {...AXIS_PROPS}
              tickFormatter={formatDayTick}
            />
            <YAxis
              {...AXIS_PROPS}
              tickFormatter={formatUsdTick}
              domain={priceRange}
              allowDecimals={false}
            />
            <Tooltip content={<CustomTooltip />} />
            <Legend />

            {/* 주가 라인 */}
            <Line
              type="monotone"
              dataKey="price"
              name={`${symbol} 주가`}
              stroke="#3b82f6"
              strokeWidth={2}
              dot={false}
              isAnimationActive={false}
            />

            {/* 매수 시점 마커 */}
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

            {/* 최적 매수 시점 마커 (금색) - 주가 차트에만 표시 */}
            {optimalBuyPoint && (
              <ReferenceDot
                x={optimalBuyPoint.date}
                y={optimalBuyPoint.price}
                {...OPTIMAL_MARKER_PROPS}
                r={6}
                label={{ value: "최적 매수", position: "top", ...MARKER_LABEL_STYLE }}
              />
            )}

            {/* 배당 재투자 시점 마커들 */}
            {dividendReinvestPoints.map((point, idx) => (
              <ReferenceDot
                key={`dividend-${idx}`}
                x={point.date}
                y={point.price}
                {...MARKER_PROPS}
                fill={MARKER_COLORS.dividend}
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
        <p className="text-xs text-tx-2 mt-2 text-center">
          파란 선 = 주가 | 보라 점 = 매수 | 녹색 점 = 배당 재투자 | 금색 점 = 최적 매수
        </p>
      </div>

      {/* 포트폴리오 가치 차트 */}
      <div className="bg-surface rounded-lg border border-line p-4">
        <h3 className="text-lg font-semibold text-tx-1 mb-4">
          투자금 vs 평가금액
        </h3>
        <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
          <LineChart
            data={chartData}
            margin={CHART_MARGIN}
          >
            <CartesianGrid {...GRID_PROPS} />
            <XAxis
              dataKey="date"
              {...AXIS_PROPS}
              tickFormatter={formatDayTick}
            />
            <YAxis
              {...AXIS_PROPS}
              tickFormatter={formatManwonTick}
            />
            <Tooltip content={<CustomTooltip />} />
            <Legend />

            {/* 투자금 라인 (수평선) */}
            <Line
              type="monotone"
              dataKey="투자금"
              name="투자금"
              stroke="#10b981"
              strokeWidth={2}
              dot={false}
              strokeDasharray="5 5"
              isAnimationActive={false}
            />

            {/* 평가금액 라인 */}
            <Line
              type="monotone"
              dataKey="평가금액"
              name="평가금액"
              stroke="#3b82f6"
              strokeWidth={2}
              dot={false}
              isAnimationActive={false}
            />

            {/* 매수 시점 마커 */}
            {purchasePoint && (
              <ReferenceDot
                x={purchasePoint.date}
                y={purchasePoint.투자금}
                {...MARKER_PROPS}
                r={6}
                fill={MARKER_COLORS.purchase}
                label={{ value: "매수", position: "top", ...MARKER_LABEL_STYLE }}
              />
            )}

            {/* 최적 매도 시점 마커 (평가금액 기준) - 평가금액 차트에만 표시 */}
            {optimalSellPoint && (
              <ReferenceDot
                x={optimalSellPoint.date}
                y={optimalSellPoint.평가금액}
                {...OPTIMAL_MARKER_PROPS}
                r={6}
                label={{ value: "최적 매도", position: "bottom", ...MARKER_LABEL_STYLE }}
              />
            )}

            {/* 배당 재투자 시점 마커들 */}
            {dividendReinvestPoints.map((point, idx) => (
              <ReferenceDot
                key={`dividend-portfolio-${idx}`}
                x={point.date}
                y={point.평가금액}
                {...MARKER_PROPS}
                fill={MARKER_COLORS.dividend}
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
        <p className="text-xs text-tx-2 mt-2 text-center">
          녹색 점선 = 투자금 | 파란 선 = 평가금액 | 보라 점 = 매수 | 녹색 점 = 배당
          재투자 | 금색 점 = 최적 매도
        </p>
      </div>
    </div>
  );
};
