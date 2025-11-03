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

interface Transaction {
  date: string;
  actualDate?: string;
  price: number;
  shares: number;
  amount: number;
  fxRate: number;
  trigger?: string;
}

interface StrategyBacktestChartProps {
  symbol: string;
  transactions: Transaction[];
  currentPrice: number;
  currentValueKrw: number;
  totalInvested: number;
  startDate: string;
  endDate?: string;
}

interface ChartDataPoint {
  date: string;
  stockPrice: number;
  portfolioValue: number;
  investedAmount: number;
  isPurchase: boolean;
}

export const DCAChart: React.FC<StrategyBacktestChartProps> = ({
  symbol,
  transactions,
  currentPrice,
  currentValueKrw,
  totalInvested,
  startDate,
  endDate
}) => {
  const [chartData, setChartData] = useState<ChartDataPoint[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 공통 차트 기간 훅 사용
  const {
    chartPeriod,
    customStartDate,
    showCustomInput,
    setChartPeriod,
    setCustomStartDate,
    getStartDateFromPeriod
  } = useChartPeriod('purchase');

  // 첫 매수일과 마지막 매수일
  const firstPurchaseDate = transactions[0]?.actualDate || transactions[0]?.date || startDate;
  const lastPurchaseDate = transactions[transactions.length - 1]?.actualDate ||
                          transactions[transactions.length - 1]?.date ||
                          endDate ||
                          new Date().toISOString().split('T')[0];

  // 차트 시작일 계산 - useMemo로 감싸서 chartPeriod 변경 시 재계산
  const chartStartDate = useMemo(() => {
    return getStartDateFromPeriod(chartPeriod, firstPurchaseDate, customStartDate);
  }, [chartPeriod, customStartDate, firstPurchaseDate, getStartDateFromPeriod]);

  useEffect(() => {
    const fetchChartData = async () => {
      setLoading(true);
      setError(null);

      try {
        const today = new Date().toISOString().split('T')[0];
        const response = await stockApi.getOHLCData(symbol, chartStartDate, today);

        // API는 flat 배열을 반환: [{symbol, date, closePrice, ...}, ...]
        let priceData = Array.isArray(response.data)
          ? response.data.filter((item: any) => item.symbol === symbol)
          : [];

        // 각 날짜의 환율 데이터 가져오기 (Bulk API 사용)
        const fxRateMap = new Map<string, number>();
        const DEFAULT_FX_RATE = 1350; // Fallback 환율

        // 모든 날짜를 한 번에 조회 (Bulk API)
        const dates = priceData.map((item: any) => item.date);
        try {
          const fxRateResponse = await stockApi.getExchangeRatesBulk(dates);
          const rates = fxRateResponse.data;

          // Map으로 변환
          Object.entries(rates).forEach(([dateStr, rate]) => {
            fxRateMap.set(dateStr, rate || DEFAULT_FX_RATE);
          });

          // 누락된 날짜는 DEFAULT로 채우기
          dates.forEach(dateStr => {
            if (!fxRateMap.has(dateStr)) {
              fxRateMap.set(dateStr, DEFAULT_FX_RATE);
            }
          });
        } catch (err) {
          // Bulk 조회 실패 시 모든 날짜에 DEFAULT 사용
          console.warn('Bulk 환율 조회 실패, fallback 환율 사용:', err);
          dates.forEach(dateStr => {
            fxRateMap.set(dateStr, DEFAULT_FX_RATE);
          });
        }

        // 거래 정보를 날짜별 맵으로 변환
        const transactionMap = new Map<string, { shares: number; investedAmount: number }>();
        let cumulativeShares = 0;
        let cumulativeInvested = 0;

        transactions.forEach(tx => {
          const txDate = tx.actualDate || tx.date;
          cumulativeShares += tx.shares;
          cumulativeInvested += tx.amount;
          transactionMap.set(txDate, {
            shares: cumulativeShares,
            investedAmount: cumulativeInvested
          });
        });

        // priceData에서 사용 가능한 날짜들을 먼저 추출
        const availableDates = priceData.map((item: any) => item.date).sort();

        // 매수 날짜를 실제 거래일로 매핑 (주말/휴일 -> 다음 영업일)
        const purchaseDates = new Set<string>();
        transactions.forEach(tx => {
          const txDate = tx.actualDate || tx.date;
          // priceData에 정확한 날짜가 있으면 사용
          if (availableDates.includes(txDate)) {
            purchaseDates.add(txDate);
          } else {
            // 없으면 다음 영업일 찾기
            const nextTradingDay = availableDates.find(d => d > txDate);
            if (nextTradingDay) {
              purchaseDates.add(nextTradingDay);
            }
          }
        });

        // 차트 데이터 생성
        const data: ChartDataPoint[] = priceData.map((item: any) => {
          const dateStr = item.date;

          // 이 날짜까지의 누적 투자금과 보유 주식 계산
          let investedSoFar = 0;
          let sharesSoFar = 0;

          for (const tx of transactions) {
            const txDate = tx.actualDate || tx.date;
            if (txDate <= dateStr) {
              investedSoFar += tx.amount;
              sharesSoFar += tx.shares;
            }
          }

          // 해당 날짜의 환율 사용 (각 날짜마다 다른 환율 적용)
          const historicalFxRate = fxRateMap.get(dateStr) || 1350;

          // 포트폴리오 가치 = 보유주식 * 현재가격 (원화 환산)
          const adjustedPrice = item.adjustedClose || item.closePrice;
          const portfolioValueUsd = sharesSoFar * adjustedPrice;
          const portfolioValueKrw = portfolioValueUsd * historicalFxRate;

          return {
            date: dateStr,
            stockPrice: adjustedPrice,
            portfolioValue: portfolioValueKrw,
            investedAmount: investedSoFar,
            isPurchase: purchaseDates.has(dateStr)  // 매수 날짜인지 체크
          };
        });

        setChartData(data);
      } catch (err: any) {
        setError(err.message || '차트 데이터를 불러오는데 실패했습니다');
      } finally {
        setLoading(false);
      }
    };

    if (symbol && transactions.length > 0) {
      fetchChartData();
    }
  }, [symbol, chartStartDate, transactions]);

  // Y축 범위 계산 (±50 여유)
  const priceRange = useMemo(() => {
    if (chartData.length === 0) return [0, 100];

    const prices = chartData.map(d => d.stockPrice);
    const min = Math.min(...prices);
    const max = Math.max(...prices);
    const padding = 50;

    return [Math.max(0, min - padding), max + padding];
  }, [chartData]);

  // 포트폴리오/투자금 Y축 범위 계산
  const valueRange = useMemo(() => {
    if (chartData.length === 0) return [0, 100];

    const values = chartData.map(d => Math.max(d.portfolioValue, d.investedAmount));
    const min = Math.min(...values);
    const max = Math.max(...values);
    const padding = (max - min) * 0.1;

    return [Math.max(0, min - padding), max + padding];
  }, [chartData]);

  // 매수 포인트는 chartData에서 isPurchase가 true인 것들
  const purchasePoints = useMemo(() => {
    const points = chartData.filter(d => d.isPurchase);
    return points;
  }, [chartData]);

  // 일반 매수와 배당 재투자 구분
  const regularPurchasePoints = useMemo(() => {
    return purchasePoints.filter((_, index) => {
      const tx = transactions.find(t => (t.actualDate || t.date) <= chartData[chartData.indexOf(purchasePoints[index])]?.date);
      return tx && tx.trigger !== '배당 재투자';
    });
  }, [purchasePoints, transactions, chartData]);

  const dividendReinvestPoints = useMemo(() => {
    return chartData.filter(d => {
      const tx = transactions.find(t => (t.actualDate || t.date) === d.date && t.trigger === '배당 재투자');
      return tx !== undefined;
    });
  }, [chartData, transactions]);

  if (loading) {
    return (
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
        <div className="flex justify-center items-center h-64">
          <div className="text-gray-500">차트 데이터를 불러오는 중...</div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
        <div className="flex justify-center items-center h-64">
          <div className="text-red-500">{error}</div>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 mt-6">
      <div className="mb-4">
        <h3 className="text-lg font-semibold text-gray-900 mb-2">투자 성과 차트</h3>
        <p className="text-sm text-gray-600">
          {transactions.length}회 매수 • 총 {transactions.reduce((sum, t) => sum + t.shares, 0).toFixed(4)}주 보유
        </p>
      </div>

      {/* 기간 선택 */}
      <ChartPeriodSelector
        chartPeriod={chartPeriod}
        customStartDate={customStartDate}
        showCustomInput={showCustomInput}
        onPeriodChange={setChartPeriod}
        onCustomDateChange={setCustomStartDate}
      />

      {/* 주가 추이 차트 */}
      <div className="mb-8">
        <h4 className="text-sm font-medium text-gray-700 mb-3">주가 추이</h4>
        <ResponsiveContainer width="100%" height={300}>
          <LineChart data={chartData} margin={{ top: 5, right: 30, left: 20, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
            <XAxis
              dataKey="date"
              tick={{ fontSize: 12 }}
              tickFormatter={(value) => {
                const date = new Date(value);
                return `${date.getMonth() + 1}/${date.getDate()}`;
              }}
            />
            <YAxis
              tick={{ fontSize: 12 }}
              tickFormatter={(value) => `$${Math.round(value)}`}
              domain={priceRange}
              allowDecimals={false}
            />
            <Tooltip
              contentStyle={{ backgroundColor: 'rgba(255, 255, 255, 0.95)', border: '1px solid #ccc' }}
              formatter={(value: any, name: string) => {
                if (name === '주가') return [`$${Number(value).toFixed(2)}`, name];
                return [value, name];
              }}
              labelFormatter={(label) => `날짜: ${label}`}
            />
            <Legend />
            <Line
              type="monotone"
              dataKey="stockPrice"
              stroke="#3b82f6"
              strokeWidth={2}
              dot={false}
              name="주가"
              isAnimationActive={false}
            />

            {/* 일반 매수 시점 마커 (검은색) */}
            {regularPurchasePoints.map((point, index) => (
              <ReferenceDot
                key={`purchase-${index}`}
                x={point.date}
                y={point.stockPrice}
                r={4}
                fill="#1f2937"
                stroke="#fff"
                strokeWidth={2}
              />
            ))}

            {/* 배당 재투자 시점 마커 (녹색) */}
            {dividendReinvestPoints.map((point, index) => (
              <ReferenceDot
                key={`dividend-${index}`}
                x={point.date}
                y={point.stockPrice}
                r={4}
                fill="#10b981"
                stroke="#fff"
                strokeWidth={2}
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>

      {/* 포트폴리오 가치 vs 투자금 차트 */}
      <div>
        <h4 className="text-sm font-medium text-gray-700 mb-3">포트폴리오 가치 vs 투자금</h4>
        <ResponsiveContainer width="100%" height={300}>
          <LineChart data={chartData} margin={{ top: 5, right: 30, left: 20, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
            <XAxis
              dataKey="date"
              tick={{ fontSize: 12 }}
              tickFormatter={(value) => {
                const date = new Date(value);
                return `${date.getMonth() + 1}/${date.getDate()}`;
              }}
            />
            <YAxis
              tick={{ fontSize: 12 }}
              tickFormatter={(value) => `₩${(value / 10000).toFixed(0)}만`}
              domain={valueRange}
            />
            <Tooltip
              contentStyle={{ backgroundColor: 'rgba(255, 255, 255, 0.95)', border: '1px solid #ccc' }}
              formatter={(value: any, name: string) => {
                return [`₩${Math.round(Number(value)).toLocaleString()}`, name];
              }}
              labelFormatter={(label) => `날짜: ${label}`}
            />
            <Legend />
            <Line
              type="stepAfter"
              dataKey="investedAmount"
              stroke="#10b981"
              strokeWidth={2}
              dot={false}
              strokeDasharray="5 5"
              name="누적 투자금"
              isAnimationActive={false}
            />
            <Line
              type="monotone"
              dataKey="portfolioValue"
              stroke="#3b82f6"
              strokeWidth={2}
              dot={false}
              name="포트폴리오 가치"
              isAnimationActive={false}
            />

            {/* 매수 시점 마커 (검은색) - 포트폴리오 값 기준 */}
            {purchasePoints.map((point, index) => (
              <ReferenceDot
                key={`portfolio-purchase-${index}`}
                x={point.date}
                y={point.portfolioValue}
                r={4}
                fill="#1f2937"
                stroke="#fff"
                strokeWidth={2}
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>

      <div className="mt-4 text-xs text-gray-500">
        <p>• ⚫ 검은 점: 매수 시점 ({purchasePoints.length}개)</p>
        <p>• 녹색 점선: 누적 투자금 (₩{totalInvested.toLocaleString()})</p>
        <p>• 파란 선: 포트폴리오 가치 (₩{currentValueKrw.toLocaleString()})</p>
      </div>
    </div>
  );
};
