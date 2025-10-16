import React, { useState, useEffect, useMemo } from 'react';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  ReferenceDot
} from 'recharts';
import { stockApi } from '../../../services/api';
import { useChartPeriod } from '../common/hooks/useChartPeriod';
import { ChartPeriodSelector } from '../common/components/ChartPeriodSelector';

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
  dividendReinvestDates
}) => {
  const [priceData, setPriceData] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  // 공통 차트 기간 훅 사용
  const {
    chartPeriod,
    customStartDate,
    showCustomInput,
    setChartPeriod,
    setCustomStartDate,
    getStartDateFromPeriod
  } = useChartPeriod('purchase');

  // 차트 기간에 따른 시작일 계산
  const getChartStartDate = () => {
    // 사용자가 선택한 기간 그대로 사용 (매수일 이전 데이터도 표시)
    return getStartDateFromPeriod(chartPeriod, purchaseDate, customStartDate);
  };

  // 일별 주가 데이터 가져오기
  useEffect(() => {
    const fetchPriceData = async () => {
      try {
        setLoading(true);
        const today = new Date().toISOString().split('T')[0];
        const startDate = getChartStartDate();
        console.log('SimpleChart fetchPriceData:', { symbol, chartPeriod, customStartDate, purchaseDate, startDate, today });
        const response = await stockApi.getOHLCData(symbol, startDate, today);

        // API는 List<OHLCPriceDto>를 반환 (flat 배열)
        // response.data = [{symbol: "AAPL", date: "...", closePrice: ...}, ...]
        let ohlcData = null;

        if (Array.isArray(response.data)) {
          // Flat 배열 - 해당 symbol만 필터링
          ohlcData = response.data.filter((item: any) => item.symbol === symbol);
        } else if (response.data && response.data.data && Array.isArray(response.data.data)) {
          // ApiResponse로 감싸진 경우
          ohlcData = response.data.data.filter((item: any) => item.symbol === symbol);
        } else if (response.data && response.data[symbol]) {
          // 이미 symbol별로 그룹화된 경우
          ohlcData = response.data[symbol];
        }

        if (ohlcData && Array.isArray(ohlcData) && ohlcData.length > 0) {
          console.log('SimpleChart OHLC data loaded:', ohlcData.length, 'records');
          setPriceData(ohlcData);
        } else {
          console.log('SimpleChart NO data loaded, response:', response.data);
        }
      } catch (error) {
        console.error('SimpleChart fetch error:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchPriceData();
  }, [symbol, purchaseDate, chartPeriod, customStartDate]);

  // 차트 데이터 생성
  const chartData = useMemo(() => {
    if (!priceData || priceData.length === 0) {
      return [];
    }

    const result = priceData.map((candle: any) => {
      // OHLCPriceDto: closePrice (BigDecimal), date (LocalDate 또는 String)
      const dailyPrice = parseFloat(candle.closePrice || candle.close || 0);
      const portfolioValue = shares * dailyPrice * fxRate;

      // date가 배열 형태로 올 수 있음: [2025, 1, 8] -> "2025-01-08"
      let dateStr = candle.date;
      if (Array.isArray(candle.date)) {
        const [year, month, day] = candle.date;
        dateStr = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
      }

      return {
        date: dateStr,
        price: dailyPrice,
        투자금: investmentAmount,
        평가금액: Math.round(portfolioValue)
      };
    });

    return result;
  }, [priceData, shares, fxRate, investmentAmount]);

  // 매수 시점 마커 - chartData에서 매수일 찾기 또는 다음 영업일 찾기
  const purchasePoint = useMemo(() => {
    if (chartData.length === 0) return null;

    // 먼저 chartData에서 정확한 날짜 찾기
    let purchaseDateData = chartData.find(d => d.date === purchaseDate);

    // chartData에 없으면 (주말/휴일), 다음 영업일 찾기
    if (!purchaseDateData) {
      // purchaseDate 이후의 첫 번째 데이터 포인트 (다음 영업일)
      purchaseDateData = chartData.find(d => d.date > purchaseDate);
    }

    return purchaseDateData || null;
  }, [chartData, purchaseDate]);

  // 최적 매수 시점 마커
  const optimalBuyPoint = useMemo(() => {
    if (chartData.length === 0 || !optimalBuyDate) return null;
    const optimalBuyData = chartData.find(d => d.date === optimalBuyDate);
    return optimalBuyData || null;
  }, [chartData, optimalBuyDate]);

  // 최적 매도 시점 마커
  const optimalSellPoint = useMemo(() => {
    if (chartData.length === 0 || !optimalSellDate) return null;
    const optimalSellData = chartData.find(d => d.date === optimalSellDate);
    return optimalSellData || null;
  }, [chartData, optimalSellDate]);

  // 배당 재투자 시점 마커들
  const dividendReinvestPoints = useMemo(() => {
    if (chartData.length === 0 || !dividendReinvestDates || dividendReinvestDates.length === 0) return [];

    return dividendReinvestDates.map(date => {
      // 먼저 chartData에서 정확한 날짜 찾기
      let reinvestDateData = chartData.find(d => d.date === date);
      // chartData에 없으면 (주말/휴일), 다음 영업일 찾기
      if (!reinvestDateData) {
        reinvestDateData = chartData.find(d => d.date > date);
      }
      return reinvestDateData;
    }).filter(point => point !== undefined);
  }, [chartData, dividendReinvestDates]);

  // Y축 범위 계산 (고점/저점에서 ±50 padding)
  const priceRange = useMemo(() => {
    if (chartData.length === 0) return [0, 100];

    const prices = chartData.map(d => d.price);
    const minPrice = Math.min(...prices);
    const maxPrice = Math.max(...prices);

    return [
      Math.max(0, minPrice - 50),
      maxPrice + 50
    ];
  }, [chartData]);

  // 커스텀 툴팁
  const CustomTooltip = ({ active, payload, label }: any) => {
    if (active && payload && payload.length) {
      const data = payload[0].payload;
      const isPurchaseDate = data.date === purchaseDate;

      return (
        <div className="bg-white p-3 border border-gray-300 rounded shadow-lg">
          <p className="font-semibold text-gray-900">{label}</p>
          {isPurchaseDate && (
            <p className="text-xs text-indigo-600 mb-1">🔵 매수 시점</p>
          )}
          <p className="text-sm text-gray-700">
            주가: ${payload.find((p: any) => p.dataKey === 'price')?.value?.toFixed(2) || '-'}
          </p>
          <p className="text-sm text-green-600">
            투자금: ₩{data.투자금?.toLocaleString()}
          </p>
          <p className="text-sm text-blue-600">
            평가금액: ₩{data.평가금액?.toLocaleString()}
          </p>
          {data.평가금액 && data.투자금 && (
            <p className={`text-sm font-semibold ${data.평가금액 >= data.투자금 ? 'text-green-600' : 'text-red-600'}`}>
              {data.평가금액 >= data.투자금 ? '수익' : '손실'}:
              ₩{Math.abs(data.평가금액 - data.투자금).toLocaleString()}
              ({((data.평가금액 / data.투자금 - 1) * 100).toFixed(2)}%)
            </p>
          )}
        </div>
      );
    }
    return null;
  };

  if (loading) {
    return (
      <div className="text-center py-8 text-gray-500">
        차트 데이터를 불러오는 중...
      </div>
    );
  }

  if (!chartData || chartData.length === 0) {
    return (
      <div className="text-center py-8 text-gray-500">
        차트 데이터가 없습니다.
      </div>
    );
  }

  return (
    <div className="space-y-6 mt-6">
      {/* 주가 차트 */}
      <div className="bg-white rounded-lg border border-gray-200 p-4">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">
          📈 {symbol} 주가 추이
        </h3>
        <ChartPeriodSelector
          chartPeriod={chartPeriod}
          customStartDate={customStartDate}
          showCustomInput={showCustomInput}
          onPeriodChange={setChartPeriod}
          onCustomDateChange={setCustomStartDate}
        />
        <ResponsiveContainer width="100%" height={300}>
          <LineChart data={chartData} margin={{ top: 5, right: 30, left: 20, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
            <XAxis
              dataKey="date"
              tick={{ fontSize: 12 }}
              tickFormatter={(value) => value.split('-').slice(1).join('/')}
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
                  value: '매수',
                  position: 'top',
                  fill: '#10b981',
                  fontSize: 12,
                  fontWeight: 'bold'
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
                  value: '최적 매수',
                  position: 'top',
                  fill: '#f59e0b',
                  fontSize: 11,
                  fontWeight: 'bold'
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
        <p className="text-xs text-gray-500 mt-2 text-center">
          파란색 라인 = 주가 | 🟢 녹색 점 = 매수/배당재투자 | 🟡 금색 점 = 최적 타이밍
        </p>
      </div>

      {/* 포트폴리오 가치 차트 */}
      <div className="bg-white rounded-lg border border-gray-200 p-4">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">
          💰 투자금 vs 평가금액
        </h3>
        <ResponsiveContainer width="100%" height={300}>
          <LineChart data={chartData} margin={{ top: 5, right: 30, left: 20, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
            <XAxis
              dataKey="date"
              tick={{ fontSize: 12 }}
              tickFormatter={(value) => value.split('-').slice(1).join('/')}
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
                  value: '매수',
                  position: 'top',
                  fill: '#10b981',
                  fontSize: 12,
                  fontWeight: 'bold'
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
                  value: '최적 매도',
                  position: 'top',
                  fill: '#f59e0b',
                  fontSize: 11,
                  fontWeight: 'bold'
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
        <p className="text-xs text-gray-500 mt-2 text-center">
          녹색 점선 = 투자금 | 파란색 = 평가금액 | 🟢 녹색 점 = 매수/배당재투자 | 🟡 금색 점 = 최적 타이밍
        </p>
      </div>
    </div>
  );
};
