import React, { useState, useEffect } from 'react';
import {
  LineChart,
  Line,
  ReferenceDot,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer
} from 'recharts';
import { DEFAULT_FX_RATE, fetchFxRates } from './shared/fxRates';
import { fetchSymbolPrices } from './shared/prices';
import {
  AXIS_PROPS,
  CHART_HEIGHT,
  CHART_MARGIN,
  formatDayTick,
  formatManwonTick,
  formatTooltipDate,
  formatUsdTick,
  GRID_PROPS,
  MARKER_COLORS,
  MARKER_PROPS,
  OPTIMAL_MARKER_PROPS,
  TOOLTIP_PROPS,
} from './shared/chartStyle';
import { useChartPeriod } from '../common/hooks/useChartPeriod';
import { ChartPeriodSelector } from '../common/components/ChartPeriodSelector';
import { CHART_COLORS } from '../common/constants';
import type { OHLCData } from '../../../types/ohlc';
import { calculateOptimalPoints } from './optimalTiming';
import { getTodayString } from '../../../utils/dateUtils';

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
        const today = endDate || getTodayString();

        // 공통 훅을 사용하여 시작일 계산
        const apiStartDate = getStartDateFromPeriod(chartPeriod, startDate, customStartDate);

        // 모든 종목의 OHLC 데이터를 병렬로 조회
        const dataPromises = symbols.map(symbolData =>
          fetchSymbolPrices<OHLCData>(symbolData.symbol, apiStartDate, today)
            .then(data => ({ symbol: symbolData.symbol, data }))
            .catch(err => {
              // 한 종목이 실패해도 나머지는 그린다
              console.warn('종목 데이터 조회 실패', symbolData.symbol, err);
              return { symbol: symbolData.symbol, data: [] };
            })
        );

        const results = await Promise.all(dataPromises);

        // 모든 종목의 모든 날짜 환율을 한 번에 받는다
        const allDates = new Set<string>();
        results.forEach(({ data }) => {
          data.forEach((item: OHLCData) => {
            allDates.add(item.date);
          });
        });
        const fxRateMap = await fetchFxRates(Array.from(allDates));

        // 날짜별로 모든 종목 데이터 병합
        const dateMap = new Map<string, ChartDataPoint>();

        results.forEach(({ symbol, data }, index) => {
          const symbolData = symbols[index];

          data.forEach((item: OHLCData) => {
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

        // 최적 매수/매도 포인트. 계산은 optimalTiming.ts 에 있다.
        // 매수일 <= 매도일 제약이 걸려 있어야 하고, 백엔드와 같은 방식이라 따로 뺐다.
        const optimalPointsData = calculateOptimalPoints(symbols, chartDataArray);

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
      } catch (err) {
        console.error('비교 차트 데이터 조회 실패', err);
        setError('차트 데이터를 가져오는데 실패했습니다.');
      } finally {
        setLoading(false);
      }
    };

    fetchChartData();
  }, [symbols, startDate, endDate, chartPeriod, customStartDate, getStartDateFromPeriod, onOptimalPointsCalculated]);

  if (loading) {
    return (
      <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6">
        <div className="flex items-center justify-center h-64">
          <div className="text-tx-2">차트 데이터 로딩 중...</div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6">
        <div className="flex items-center justify-center h-64">
          <div className="text-danger">{error}</div>
        </div>
      </div>
    );
  }

  if (chartData.length === 0) {
    return (
      <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6">
        <div className="flex items-center justify-center h-64">
          <div className="text-tx-2">차트 데이터가 없습니다.</div>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6 space-y-6">
      <div className="space-y-4">
        <h3 className="text-lg font-semibold text-tx-1">종목별 비교 차트</h3>
        <ChartPeriodSelector
          chartPeriod={chartPeriod}
          customStartDate={customStartDate}
          onPeriodChange={setChartPeriod}
          onCustomDateChange={setCustomStartDate}
        />
      </div>

      {/* Stock Price Comparison Chart */}
      <div>
        <h4 className="text-sm font-medium text-tx-1 mb-3">주가 추이 비교</h4>
        <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
          <LineChart data={chartData} margin={CHART_MARGIN}>
            <CartesianGrid {...GRID_PROPS} />
            <XAxis
              dataKey="date"
              {...AXIS_PROPS}
              tickFormatter={formatDayTick}
            />
            <YAxis
              domain={priceDomain}
              {...AXIS_PROPS}
              tickFormatter={formatUsdTick}
            />
            <Tooltip
              {...TOOLTIP_PROPS}
              formatter={(value: number | string, name: string) => {
                if (name.endsWith('_price')) {
                  const symbol = name.replace('_price', '');
                  return [`$${Number(value).toFixed(2)}`, `${symbol} 주가`];
                }
                return [value, name];
              }}
              labelFormatter={formatTooltipDate}
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

            {/* 매수 포인트 마커 */}
            {symbols.map((symbolData) => {
              // 정확한 날짜 또는 매수일 이후 첫 번째 날짜 찾기
              const purchasePoint = chartData.find(d => d.date >= symbolData.purchaseDate);
              if (!purchasePoint) return null;

              return (
                <ReferenceDot
                  key={`${symbolData.symbol}-purchase`}
                  x={purchasePoint.date}
                  y={purchasePoint[`${symbolData.symbol}_price`]}
                  {...MARKER_PROPS}
                  fill={MARKER_COLORS.purchase}
                  label={{ value: '', position: 'top' }}
                />
              );
            })}

            {/* 최적 매수 포인트 마커 */}
            {symbols.map((symbolData) => {
              const optimalPoint = optimalPoints[symbolData.symbol];
              if (!optimalPoint) return null;

              const buyPoint = chartData.find(d => d.date === optimalPoint.buyDate);
              if (!buyPoint) return null;

              return (
                <ReferenceDot
                  key={`${symbolData.symbol}-optimal-buy`}
                  x={buyPoint.date}
                  y={buyPoint[`${symbolData.symbol}_price`]}
                  {...OPTIMAL_MARKER_PROPS}
                  label={{ value: '', position: 'top' }}
                />
              );
            })}
          </LineChart>
        </ResponsiveContainer>
      </div>

      {/* Portfolio Value Comparison Chart */}
      <div>
        <h4 className="text-sm font-medium text-tx-1 mb-3">종목별 평가금액 추이</h4>
        <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
          <LineChart data={chartData} margin={CHART_MARGIN}>
            <CartesianGrid {...GRID_PROPS} />
            <XAxis
              dataKey="date"
              {...AXIS_PROPS}
              tickFormatter={formatDayTick}
            />
            <YAxis
              domain={portfolioDomain}
              {...AXIS_PROPS}
              tickFormatter={formatManwonTick}
            />
            <Tooltip
              {...TOOLTIP_PROPS}
              formatter={(value: number | string, name: string) => {
                if (name.endsWith('_portfolio')) {
                  const symbol = name.replace('_portfolio', '');
                  return [`₩${Number(value).toLocaleString()}`, `${symbol} 평가금`];
                }
                if (name === '투자금') {
                  return [`₩${Number(value).toLocaleString()}`, name];
                }
                return [value, name];
              }}
              labelFormatter={formatTooltipDate}
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

            {/* 매수 포인트 마커 - 포트폴리오 차트 */}
            {symbols.map((symbolData) => {
              // 정확한 날짜 또는 매수일 이후 첫 번째 날짜 찾기
              const purchasePoint = chartData.find(d => d.date >= symbolData.purchaseDate);
              if (!purchasePoint) return null;

              return (
                <ReferenceDot
                  key={`${symbolData.symbol}-purchase-portfolio`}
                  x={purchasePoint.date}
                  y={purchasePoint[`${symbolData.symbol}_portfolio`]}
                  {...MARKER_PROPS}
                  fill={MARKER_COLORS.purchase}
                  label={{ value: '', position: 'top' }}
                />
              );
            })}

            {/* 최적 매도 포인트 마커 */}
            {symbols.map((symbolData) => {
              const optimalPoint = optimalPoints[symbolData.symbol];
              if (!optimalPoint) return null;

              const sellPoint = chartData.find(d => d.date === optimalPoint.sellDate);
              if (!sellPoint) return null;

              return (
                <ReferenceDot
                  key={`${symbolData.symbol}-optimal-sell`}
                  x={sellPoint.date}
                  y={sellPoint[`${symbolData.symbol}_portfolio`]}
                  {...OPTIMAL_MARKER_PROPS}
                  label={{ value: '', position: 'top' }}
                />
              );
            })}
          </LineChart>
        </ResponsiveContainer>
        <div className="mt-3 p-3 bg-warning-soft/10 border border-warning-soft/40 rounded-lg">
          <p className="text-xs text-tx-1">
            <span className="font-semibold">차트 마커 안내:</span><br/>
            <span className="inline-block w-3 h-3 rounded-full mr-1 align-middle" style={{ backgroundColor: MARKER_COLORS.purchase }}></span> 보라 점 = 실제 매수 시점 |
            <span className="inline-block w-3 h-3 rounded-full mr-1 ml-2 align-middle" style={{ backgroundColor: MARKER_COLORS.optimal }}></span> 금색 점 = 최적 매수/매도 시점 (가장 낮은 가격 / 가장 높은 평가금액)
          </p>
        </div>
      </div>
    </div>
  );
};
