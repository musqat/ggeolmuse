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
import { stockApi, accountsApi } from "../../../services/api";
import { useChartPeriod } from "../common/hooks/useChartPeriod";
import { ChartPeriodSelector } from "../common/components/ChartPeriodSelector";

interface SimpleBacktestChartProps {
  symbol: string;
  purchaseDate: string;
  purchasePrice: number;
  shares: number;
  investmentAmount: number;
  currentPrice: number;
  currentValueKrw: number;
  fxRate: number;
  optimalBuyDate?: string;
  optimalBuyPrice?: number;
  optimalSellDate?: string;
  optimalSellPrice?: number;
  dividendReinvestDates?: string[];
}

export const SimpleChart: React.FC<SimpleBacktestChartProps> = ({
  symbol,
  purchaseDate,
  purchasePrice,
  shares,
  investmentAmount,
  currentPrice,
  currentValueKrw,
  fxRate,
  optimalBuyDate,
  optimalBuyPrice,
  optimalSellDate,
  optimalSellPrice,
  dividendReinvestDates,
}) => {
  // 공통 차트 기간 훅 사용
  const {
    chartPeriod,
    customStartDate,
    showCustomInput,
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
      chartPeriod,
      customStartDate,
    ],
    queryFn: async () => {
      const today = new Date().toISOString().split("T")[0];
      const startDate = getChartStartDate();
      console.log("SimpleChart fetchPriceData:", {
        symbol,
        chartPeriod,
        customStartDate,
        purchaseDate,
        startDate,
        today,
      });
      const response = await stockApi.getOHLCData(symbol, startDate, today);

      // API는 List<OHLCPriceDto>를 반환 (flat 배열)
      let ohlcData = null;

      if (Array.isArray(response.data)) {
        ohlcData = response.data.filter((item: any) => item.symbol === symbol);
      } else if (
        response.data &&
        response.data.data &&
        Array.isArray(response.data.data)
      ) {
        ohlcData = response.data.data.filter(
          (item: any) => item.symbol === symbol,
        );
      } else if (response.data && response.data[symbol]) {
        ohlcData = response.data[symbol];
      }

      if (ohlcData && Array.isArray(ohlcData) && ohlcData.length > 0) {
        console.log(
          "SimpleChart OHLC data loaded:",
          ohlcData.length,
          "records",
        );

        // 각 날짜의 환율 데이터 가져오기 (Bulk API 사용)
        const fxRateMap = new Map<string, number>();
        const DEFAULT_FX_RATE = 1350; // Fallback 환율

        // 모든 날짜 수집
        const allDates = ohlcData.map((item: any) =>
          Array.isArray(item.date)
            ? `${item.date[0]}-${String(item.date[1]).padStart(2, "0")}-${String(item.date[2]).padStart(2, "0")}`
            : item.date,
        );

        // Bulk API로 한 번에 환율 조회
        try {
          const bulkResponse = await stockApi.getExchangeRatesBulk(allDates);
          const ratesData = bulkResponse.data;

          // Map에 저장
          Object.entries(ratesData).forEach(([date, rate]) => {
            const rateValue =
              typeof rate === "number" ? rate : parseFloat(String(rate));
            fxRateMap.set(
              date,
              !isNaN(rateValue) && rateValue > 0 ? rateValue : DEFAULT_FX_RATE,
            );
          });

          // 누락된 날짜는 fallback 사용
          allDates.forEach((date) => {
            if (!fxRateMap.has(date)) {
              fxRateMap.set(date, DEFAULT_FX_RATE);
            }
          });
        } catch (err) {
          console.log("Bulk FX rate fetch failed, using fallback:", err);
          // 모든 날짜에 fallback 적용
          allDates.forEach((date) => fxRateMap.set(date, DEFAULT_FX_RATE));
        }

        // OHLC 데이터와 환율 매핑을 함께 저장
        return ohlcData.map((item: any) => {
          const dateStr = Array.isArray(item.date)
            ? `${item.date[0]}-${String(item.date[1]).padStart(2, "0")}-${String(item.date[2]).padStart(2, "0")}`
            : item.date;

          return {
            ...item,
            fxRate: fxRateMap.get(dateStr) || DEFAULT_FX_RATE,
          };
        });
      } else {
        console.log("SimpleChart NO data loaded, response:", response.data);
        return [];
      }
    },
    staleTime: 5 * 60 * 1000, // 5분
  });

  // 차트 데이터 생성
  const chartData = useMemo(() => {
    if (!priceData || priceData.length === 0) {
      return [];
    }

    const result = priceData.map((candle: any) => {
      // OHLCPriceDto: adjustedClose (배당/주식분할 반영된 보정 종가)
      const dailyPrice = parseFloat(
        candle.adjustedClose || candle.closePrice || candle.close || 0,
      );

      // date가 배열 형태로 올 수 있음: [2025, 1, 8] -> "2025-01-08"
      let dateStr = candle.date;
      if (Array.isArray(candle.date)) {
        const [year, month, day] = candle.date;
        dateStr = `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
      }

      // 해당 날짜의 환율 사용 (각 날짜마다 다른 환율 적용)
      const historicalFxRate = candle.fxRate || 1350;

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
  const CustomTooltip = ({ active, payload, label }: any) => {
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
          <p className="text-sm text-green-600">
            투자금: ₩{data.투자금?.toLocaleString()}
          </p>
          <p className="text-sm text-blue-600">
            평가금액: ₩{data.평가금액?.toLocaleString()}
          </p>
          {data.평가금액 && data.투자금 && (
            <p
              className={`text-sm font-semibold ${data.평가금액 >= data.투자금 ? "text-green-600" : "text-red-600"}`}
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
          showCustomInput={showCustomInput}
          onPeriodChange={setChartPeriod}
          onCustomDateChange={setCustomStartDate}
        />
        <ResponsiveContainer width="100%" height={300}>
          <LineChart
            data={chartData}
            margin={{ top: 5, right: 30, left: 20, bottom: 5 }}
          >
            <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
            <XAxis
              dataKey="date"
              tick={{ fontSize: 12 }}
              tickFormatter={(value) => value.split("-").slice(1).join("/")}
            />
            <YAxis
              tick={{ fontSize: 12 }}
              tickFormatter={(value) => `$${Math.round(value)}`}
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
                r={6}
                fill="#10b981"
                stroke="#fff"
                strokeWidth={2}
                label={{
                  value: "매수",
                  position: "top",
                  fill: "#10b981",
                  fontSize: 12,
                  fontWeight: "bold",
                }}
              />
            )}

            {/* 최적 매수 시점 마커 (금색) - 주가 차트에만 표시 */}
            {optimalBuyPoint && (
              <ReferenceDot
                x={optimalBuyPoint.date}
                y={optimalBuyPoint.price}
                r={6}
                fill="#fbbf24"
                stroke="#78350f"
                strokeWidth={2}
                label={{
                  value: "최적 매수",
                  position: "top",
                  fill: "#f59e0b",
                  fontSize: 11,
                  fontWeight: "bold",
                }}
              />
            )}

            {/* 배당 재투자 시점 마커들 */}
            {dividendReinvestPoints.map((point, idx) => (
              <ReferenceDot
                key={`dividend-${idx}`}
                x={point.date}
                y={point.price}
                r={4}
                fill="#10b981"
                stroke="#fff"
                strokeWidth={2}
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
        <p className="text-xs text-tx-2 mt-2 text-center">
          파란색 라인 = 주가 | 녹색 점 = 매수/배당재투자 | 금색 점 = 최적 타이밍
        </p>
      </div>

      {/* 포트폴리오 가치 차트 */}
      <div className="bg-surface rounded-lg border border-line p-4">
        <h3 className="text-lg font-semibold text-tx-1 mb-4">
          투자금 vs 평가금액
        </h3>
        <ResponsiveContainer width="100%" height={300}>
          <LineChart
            data={chartData}
            margin={{ top: 5, right: 30, left: 20, bottom: 5 }}
          >
            <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
            <XAxis
              dataKey="date"
              tick={{ fontSize: 12 }}
              tickFormatter={(value) => value.split("-").slice(1).join("/")}
            />
            <YAxis
              tick={{ fontSize: 12 }}
              tickFormatter={(value) => `₩${(value / 10000).toFixed(0)}만`}
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
                r={6}
                fill="#10b981"
                stroke="#fff"
                strokeWidth={2}
                label={{
                  value: "매수",
                  position: "top",
                  fill: "#10b981",
                  fontSize: 12,
                  fontWeight: "bold",
                }}
              />
            )}

            {/* 최적 매도 시점 마커 (평가금액 기준) - 평가금액 차트에만 표시 */}
            {optimalSellPoint && (
              <ReferenceDot
                x={optimalSellPoint.date}
                y={optimalSellPoint.평가금액}
                r={6}
                fill="#fbbf24"
                stroke="#78350f"
                strokeWidth={2}
                label={{
                  value: "최적 매도",
                  position: "bottom",
                  fill: "#f59e0b",
                  fontSize: 11,
                  fontWeight: "bold",
                }}
              />
            )}

            {/* 배당 재투자 시점 마커들 */}
            {dividendReinvestPoints.map((point, idx) => (
              <ReferenceDot
                key={`dividend-portfolio-${idx}`}
                x={point.date}
                y={point.평가금액}
                r={4}
                fill="#10b981"
                stroke="#fff"
                strokeWidth={2}
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
        <p className="text-xs text-tx-2 mt-2 text-center">
          녹색 점선 = 투자금 | 파란색 = 평가금액 | 녹색 점 = 매수/배당재투자 |
          금색 점 = 최적 타이밍
        </p>
      </div>
    </div>
  );
};
