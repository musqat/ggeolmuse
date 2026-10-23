import React, { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { Line, YAxis, Tooltip, ReferenceDot } from "recharts";
import { DEFAULT_FX_RATE, fetchFxRates } from './shared/fxRates';
import { closeOf, fetchSymbolPrices } from './shared/prices';
import {
  AXIS_PROPS,
  dateAxis,
  formatKrwTick,
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

// 가격·환율·평가액은 받지 않는다. 차트가 일자별 캔들에서 직접 구한다.
// 최적 매수·매도도 날짜만 받아 그 날의 데이터를 chartData 에서 찾는다.
// 서버가 date 를 "2025-01-08" 로도, [2025, 1, 8] 배열로도 준다.
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
  const period = useChartPeriod("purchase");
  const { chartPeriod, customStartDate, getStartDateFromPeriod } = period;

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
      const dailyPrice = closeOf(candle);

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

  const xAxis = dateAxis(chartData.map((d) => d.date));

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
    return <ChartMessage>차트 데이터를 불러오는 중...</ChartMessage>;
  }

  if (!chartData || chartData.length === 0) {
    return <ChartMessage>차트 데이터가 없습니다.</ChartMessage>;
  }

  return (
    <div className="space-y-6 mt-6">
      {/* 주가 차트 */}
      <ChartCard title={`${symbol} 주가 추이`} period={period}>
        <ChartPanel data={chartData} xAxis={xAxis}>
          <YAxis
            {...AXIS_PROPS}
            tickFormatter={formatUsdTick}
            domain={priceRange}
            allowDecimals={false}
          />
          <Tooltip content={<CustomTooltip />} />

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

          {/* 배당 재투자 시점 마커들 */}
          {markerDots(dividendReinvestPoints, "price", MARKER_COLORS.dividend, "dividend")}
        </ChartPanel>
        <p className="text-xs text-tx-2 mt-2 text-center">
          파란 선 = 주가 | 보라 점 = 매수 | 녹색 점 = 배당 재투자
        </p>
      </ChartCard>

      {/* 포트폴리오 가치 차트 */}
      <ChartCard title="투자금 vs 평가금액">
        <ChartPanel data={chartData} xAxis={xAxis}>
          <YAxis
            {...AXIS_PROPS}
            tickFormatter={formatKrwTick}
          />
          <Tooltip content={<CustomTooltip />} />

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

          {/* 최적 매수 · 매도 시점 마커 (원화 주가 기준) */}
          {optimalBuyPoint && (
            <ReferenceDot
              x={optimalBuyPoint.date}
              y={optimalBuyPoint.평가금액}
              {...OPTIMAL_MARKER_PROPS}
              r={6}
              label={{ value: "최적 매수", position: "bottom", ...MARKER_LABEL_STYLE }}
            />
          )}
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
          {markerDots(dividendReinvestPoints, "평가금액", MARKER_COLORS.dividend, "dividend-portfolio")}
        </ChartPanel>
        <p className="text-xs text-tx-2 mt-2 text-center">
          녹색 점선 = 투자금 | 파란 선 = 평가금액 | 보라 점 = 매수 | 녹색 점 = 배당
          재투자 | 금색 점 = 최적 매수 · 매도
        </p>
      </ChartCard>
    </div>
  );
};
