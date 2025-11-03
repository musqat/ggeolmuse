import React, { useState, useEffect } from 'react';
import {
  ComposedChart,
  LineChart,
  Line,
  Scatter,
  ReferenceDot,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer
} from 'recharts';
import { stockApi, accountsApi } from '../../../services/api';
import { useChartPeriod } from '../common/hooks/useChartPeriod';
import { ChartPeriodSelector } from '../common/components/ChartPeriodSelector';
import { CHART_COLORS } from '../common/constants';

interface SymbolData {
  symbol: string;
  purchaseDate: string;
  purchasePrice: number;
  shares: number;
  investmentAmount: number;
  currentPrice: number;
  currentValueKrw: number;
  fxRate: number;
  color: string; // Chart line color
}

interface SymbolComparisonChartProps {
  symbols: SymbolData[];
  startDate: string;
  endDate?: string;
  onOptimalPointsCalculated?: (points: {
    [symbol: string]: { buyDate: string; sellDate: string; minPrice: number; maxValue: number };
  }) => void;
}

interface ChartDataPoint {
  date: string;
  [key: string]: string | number; // Dynamic keys for each symbol's price and portfolio value
}

export const CompareSymbolsChart: React.FC<SymbolComparisonChartProps> = ({
  symbols,
  startDate,
  endDate,
  onOptimalPointsCalculated
}) => {
  const [chartData, setChartData] = useState<ChartDataPoint[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [optimalPoints, setOptimalPoints] = useState<{
    [symbol: string]: { buyDate: string; sellDate: string; minPrice: number; maxValue: number };
  }>({});
  const [priceDomain, setPriceDomain] = useState<[number, number]>([0, 100]);
  const [portfolioDomain, setPortfolioDomain] = useState<[number, number]>([0, 1000000]);

  // 공통 차트 기간 훅 사용
  const {
    chartPeriod,
    customStartDate,
    showCustomInput,
    setChartPeriod,
    setCustomStartDate,
    getStartDateFromPeriod
  } = useChartPeriod('purchase');

  // 클라이언트 측 필터링 불필요 - API가 선택된 기간의 데이터를 반환

  useEffect(() => {
    const fetchChartData = async () => {
      if (symbols.length === 0) return;

      setLoading(true);
      setError(null);

      try {
        const today = endDate || new Date().toISOString().split('T')[0];

        // 공통 훅을 사용하여 시작일 계산
        const apiStartDate = getStartDateFromPeriod(chartPeriod, startDate, customStartDate);

        // 모든 종목의 OHLC 데이터를 병렬로 조회
        const dataPromises = symbols.map(symbolData =>
          stockApi.getOHLCData(symbolData.symbol, apiStartDate, today)
            .then(response => ({
              symbol: symbolData.symbol,
              data: Array.isArray(response.data)
                ? response.data.filter((item: any) => item.symbol === symbolData.symbol)
                : []
            }))
            .catch(err => {
              return { symbol: symbolData.symbol, data: [] };
            })
        );

        const results = await Promise.all(dataPromises);

        // 각 날짜의 환율 데이터 가져오기
        const fxRateMap = new Map<string, number>();
        const DEFAULT_FX_RATE = 1350; // Fallback 환율
        const allDates = new Set<string>();

        // 모든 종목의 모든 날짜 수집
        results.forEach(({ data }) => {
          data.forEach((item: any) => {
            allDates.add(item.date);
          });
        });

        // 모든 날짜에 대해 환율 조회 (병렬 처리)
        const fxRatePromises = Array.from(allDates).map(async (dateStr) => {
          try {
            const fxRateResponse = await accountsApi.getExchangeRateByDate(dateStr);
            const rate = typeof fxRateResponse.data === 'number'
              ? fxRateResponse.data
              : parseFloat(fxRateResponse.data);

            if (!isNaN(rate) && rate > 0) {
              fxRateMap.set(dateStr, rate);
            } else {
              fxRateMap.set(dateStr, DEFAULT_FX_RATE);
            }
          } catch (err) {
            // 환율 데이터가 없으면 fallback 사용
            fxRateMap.set(dateStr, DEFAULT_FX_RATE);
          }
        });

        await Promise.all(fxRatePromises);

        // 날짜별로 모든 종목 데이터 병합
        const dateMap = new Map<string, ChartDataPoint>();

        results.forEach(({ symbol, data }, index) => {
          const symbolData = symbols[index];

          data.forEach((item: any) => {
            const dateStr = item.date;

            if (!dateMap.has(dateStr)) {
              dateMap.set(dateStr, { date: dateStr });
            }

            const point = dateMap.get(dateStr)!;

            // 주가 저장 (adjustedClose 사용)
            const adjustedPrice = item.adjustedClose || item.closePrice;
            point[`${symbol}_price`] = adjustedPrice;

            // 매수일 이후에만 포트폴리오 가치 계산
            // purchaseDate가 비어있거나 undefined인 경우 startDate를 대체값으로 사용
            const effectivePurchaseDate = symbolData.purchaseDate || startDate;

            if (dateStr >= effectivePurchaseDate) {
              // 해당 날짜의 환율 사용 (각 날짜마다 다른 환율 적용)
              const historicalFxRate = fxRateMap.get(dateStr) || DEFAULT_FX_RATE;
              const portfolioValueKrw = symbolData.shares * adjustedPrice * historicalFxRate;
              point[`${symbol}_portfolio`] = portfolioValueKrw;
            } else {
              // 매수일 이전에는 0으로 설정
              point[`${symbol}_portfolio`] = 0;
            }

            // 산점도를 위한 매수 포인트 표시
            if (dateStr === effectivePurchaseDate) {
              point[`${symbol}_purchasePrice`] = adjustedPrice;
            }
          });
        });

        // 맵을 정렬된 배열로 변환
        const chartDataArray = Array.from(dateMap.values()).sort(
          (a, b) => new Date(a.date).getTime() - new Date(b.date).getTime()
        );

        // 각 포인트에서 총 투자금과 총 포트폴리오 가치 계산
        // 단일 투자금 사용 (모든 종목이 동일한 투자금을 가진다고 가정)
        const singleInvestmentAmount = symbols.length > 0 ? symbols[0].investmentAmount : 0;

        chartDataArray.forEach(point => {
          let totalPortfolio = 0;
          let hasAnyPurchase = false;

          symbols.forEach(symbolData => {
            // 이 종목이 아직 매수되었는지 확인
            const effectivePurchaseDate = symbolData.purchaseDate || startDate;

            if (point.date >= effectivePurchaseDate) {
              hasAnyPurchase = true;
              const portfolioValue = point[`${symbolData.symbol}_portfolio`] as number;
              if (portfolioValue && portfolioValue > 0) {
                totalPortfolio += portfolioValue;
              }
            }
          });

          // 매수가 있는 경우에만 설정
          if (hasAnyPurchase) {
            point['totalInvestment'] = singleInvestmentAmount;
            point['totalPortfolio'] = totalPortfolio;
          }
        });

        // 각 종목의 최적 매수/매도 포인트 계산
        const optimalPointsData: typeof optimalPoints = {};
        symbols.forEach(symbolData => {
          let minPrice = Infinity;
          let maxValue = 0;
          let buyDate = '';
          let sellDate = '';

          chartDataArray.forEach(point => {
            const price = point[`${symbolData.symbol}_price`] as number;
            const portfolioValue = point[`${symbolData.symbol}_portfolio`] as number;

            if (price && price < minPrice) {
              minPrice = price;
              buyDate = point.date;
            }

            if (portfolioValue && portfolioValue > maxValue) {
              maxValue = portfolioValue;
              sellDate = point.date;
            }
          });

          optimalPointsData[symbolData.symbol] = { buyDate, sellDate, minPrice, maxValue };
        });

        // 더 나은 차트 렌더링을 위한 Y축 범위 계산
        // 가격 차트 범위
        let globalMinPrice = Infinity;
        let globalMaxPrice = -Infinity;

        symbols.forEach(symbolData => {
          chartDataArray.forEach(point => {
            const price = point[`${symbolData.symbol}_price`] as number;
            if (price && price > 0) {
              if (price < globalMinPrice) globalMinPrice = price;
              if (price > globalMaxPrice) globalMaxPrice = price;
            }
          });
        });

        // 포트폴리오 차트 범위
        let globalMinValue = Infinity;
        let globalMaxValue = -Infinity;

        chartDataArray.forEach(point => {
          symbols.forEach(symbolData => {
            const value = point[`${symbolData.symbol}_portfolio`] as number;
            if (value && value > 0) {
              if (value < globalMinValue) globalMinValue = value;
              if (value > globalMaxValue) globalMaxValue = value;
            }
          });

          const investment = point['totalInvestment'] as number;
          if (investment) {
            if (investment < globalMinValue) globalMinValue = investment;
            if (investment > globalMaxValue) globalMaxValue = investment;
          }
        });

        // 더 나은 시각화를 위해 범위에 5% 여유 추가
        const priceMargin = (globalMaxPrice - globalMinPrice) * 0.05;
        const calculatedPriceDomain: [number, number] = [
          Math.floor(globalMinPrice - priceMargin),
          Math.ceil(globalMaxPrice + priceMargin)
        ];

        const valueMargin = (globalMaxValue - globalMinValue) * 0.05;
        const calculatedPortfolioDomain: [number, number] = [
          Math.floor(globalMinValue - valueMargin),
          Math.ceil(globalMaxValue + valueMargin)
        ];

        setOptimalPoints(optimalPointsData);
        setPriceDomain(calculatedPriceDomain);
        setPortfolioDomain(calculatedPortfolioDomain);
        setChartData(chartDataArray);

        // 부모 컴포넌트에 알림
        if (onOptimalPointsCalculated) {
          onOptimalPointsCalculated(optimalPointsData);
        }
      } catch (err: any) {
        setError('차트 데이터를 가져오는데 실패했습니다.');
      } finally {
        setLoading(false);
      }
    };

    fetchChartData();
  }, [symbols, startDate, endDate, chartPeriod, customStartDate]);

  if (loading) {
    return (
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
        <div className="flex items-center justify-center h-64">
          <div className="text-gray-500">차트 데이터 로딩 중...</div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
        <div className="flex items-center justify-center h-64">
          <div className="text-red-500">{error}</div>
        </div>
      </div>
    );
  }

  if (chartData.length === 0) {
    return (
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
        <div className="flex items-center justify-center h-64">
          <div className="text-gray-500">차트 데이터가 없습니다.</div>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 space-y-6">
      <div className="space-y-4">
        <h3 className="text-lg font-semibold text-gray-900">종목별 비교 차트</h3>
        <ChartPeriodSelector
          chartPeriod={chartPeriod}
          customStartDate={customStartDate}
          showCustomInput={showCustomInput}
          onPeriodChange={setChartPeriod}
          onCustomDateChange={setCustomStartDate}
        />
      </div>

      {/* Stock Price Comparison Chart */}
      <div>
        <h4 className="text-sm font-medium text-gray-700 mb-3">주가 추이 비교</h4>
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
              domain={priceDomain}
              tick={{ fontSize: 12 }}
              tickFormatter={(value) => `$${value.toFixed(0)}`}
            />
            <Tooltip
              contentStyle={{ backgroundColor: 'rgba(255, 255, 255, 0.95)', border: '1px solid #ccc' }}
              formatter={(value: any, name: string) => {
                if (name.endsWith('_price')) {
                  const symbol = name.replace('_price', '');
                  return [`$${Number(value).toFixed(2)}`, `${symbol} 주가`];
                }
                return [value, name];
              }}
              labelFormatter={(label) => `날짜: ${label}`}
            />
            <Legend />
            {symbols.map((symbolData, index) => (
              <Line
                key={symbolData.symbol}
                type="monotone"
                dataKey={`${symbolData.symbol}_price`}
                stroke={symbolData.color || CHART_COLORS[index % CHART_COLORS.length]}
                strokeWidth={2}
                dot={false}
                name={`${symbolData.symbol} 주가`}
                isAnimationActive={false}
              />
            ))}

            {/* 매수 포인트 마커 (검은색) */}
            {symbols.map((symbolData, index) => {
              // 정확한 날짜 또는 매수일 이후 첫 번째 날짜 찾기
              const purchasePoint = chartData.find(d => d.date >= symbolData.purchaseDate);
              if (!purchasePoint) return null;

              return (
                <ReferenceDot
                  key={`${symbolData.symbol}-purchase`}
                  x={purchasePoint.date}
                  y={purchasePoint[`${symbolData.symbol}_price`]}
                  r={4}
                  fill="#1f2937"
                  stroke="#fff"
                  strokeWidth={2}
                  label={{ value: '', position: 'top' }}
                />
              );
            })}

            {/* 최적 매수 포인트 마커 (금색) */}
            {symbols.map((symbolData, index) => {
              const optimalPoint = optimalPoints[symbolData.symbol];
              if (!optimalPoint) return null;

              const buyPoint = chartData.find(d => d.date === optimalPoint.buyDate);
              if (!buyPoint) return null;

              return (
                <ReferenceDot
                  key={`${symbolData.symbol}-optimal-buy`}
                  x={buyPoint.date}
                  y={buyPoint[`${symbolData.symbol}_price`]}
                  r={5}
                  fill="#fbbf24"
                  stroke="#78350f"
                  strokeWidth={2}
                  label={{ value: '', position: 'top' }}
                />
              );
            })}
          </LineChart>
        </ResponsiveContainer>
      </div>

      {/* Portfolio Value Comparison Chart */}
      <div>
        <h4 className="text-sm font-medium text-gray-700 mb-3">종목별 평가금액 추이</h4>
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
              domain={portfolioDomain}
              tick={{ fontSize: 12 }}
              tickFormatter={(value) => `₩${(value / 10000).toFixed(0)}만`}
            />
            <Tooltip
              contentStyle={{ backgroundColor: 'rgba(255, 255, 255, 0.95)', border: '1px solid #ccc' }}
              formatter={(value: any, name: string) => {
                if (name.endsWith('_portfolio')) {
                  const symbol = name.replace('_portfolio', '');
                  return [`₩${Number(value).toLocaleString()}`, `${symbol} 평가금`];
                }
                if (name === '투자금') {
                  return [`₩${Number(value).toLocaleString()}`, name];
                }
                return [value, name];
              }}
              labelFormatter={(label) => `날짜: ${label}`}
            />
            <Legend />

            {/* 투자금 기준선 */}
            <Line
              type="monotone"
              dataKey="totalInvestment"
              name="투자금"
              stroke="#10b981"
              strokeWidth={2}
              dot={false}
              strokeDasharray="5 5"
              isAnimationActive={false}
            />

            {/* 개별 포트폴리오 가치 */}
            {symbols.map((symbolData, index) => (
              <Line
                key={symbolData.symbol}
                type="monotone"
                dataKey={`${symbolData.symbol}_portfolio`}
                stroke={symbolData.color || CHART_COLORS[index % CHART_COLORS.length]}
                strokeWidth={2}
                dot={false}
                name={`${symbolData.symbol} 평가금`}
                isAnimationActive={false}
              />
            ))}

            {/* 매수 포인트 마커 (검은색) - 포트폴리오 차트 */}
            {symbols.map((symbolData, index) => {
              // 정확한 날짜 또는 매수일 이후 첫 번째 날짜 찾기
              const purchasePoint = chartData.find(d => d.date >= symbolData.purchaseDate);
              if (!purchasePoint) return null;

              return (
                <ReferenceDot
                  key={`${symbolData.symbol}-purchase-portfolio`}
                  x={purchasePoint.date}
                  y={purchasePoint[`${symbolData.symbol}_portfolio`]}
                  r={4}
                  fill="#1f2937"
                  stroke="#fff"
                  strokeWidth={2}
                  label={{ value: '', position: 'top' }}
                />
              );
            })}

            {/* 최적 매도 포인트 마커 (금색) */}
            {symbols.map((symbolData, index) => {
              const optimalPoint = optimalPoints[symbolData.symbol];
              if (!optimalPoint) return null;

              const sellPoint = chartData.find(d => d.date === optimalPoint.sellDate);
              if (!sellPoint) return null;

              return (
                <ReferenceDot
                  key={`${symbolData.symbol}-optimal-sell`}
                  x={sellPoint.date}
                  y={sellPoint[`${symbolData.symbol}_portfolio`]}
                  r={5}
                  fill="#fbbf24"
                  stroke="#78350f"
                  strokeWidth={2}
                  label={{ value: '', position: 'top' }}
                />
              );
            })}
          </LineChart>
        </ResponsiveContainer>
        <div className="mt-3 p-3 bg-amber-50 border border-amber-200 rounded-lg">
          <p className="text-xs text-gray-700">
            <span className="font-semibold">차트 마커 안내:</span><br/>
            <span className="inline-block w-3 h-3 bg-gray-800 rounded-full mr-1 align-middle"></span> 검은색 점 = 실제 매수 시점 |
            <span className="inline-block w-3 h-3 bg-amber-400 rounded-full mr-1 ml-2 align-middle"></span> 금색 점 = 최적 매수/매도 시점 (가장 낮은 가격 / 가장 높은 평가금액)
          </p>
        </div>
      </div>
    </div>
  );
};
