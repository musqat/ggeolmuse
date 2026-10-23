import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Line, ReferenceDot, YAxis, Tooltip } from 'recharts';
import { DEFAULT_FX_RATE, fetchFxRates } from './shared/fxRates';
import { closeOf, fetchSymbolPrices } from './shared/prices';
import {
  AXIS_PROPS,
  dateAxis,
  formatKrwTick,
  formatTooltipDate,
  formatUsdTick,
  MARKER_COLORS,
  OPTIMAL_MARKER_PROPS,
  TOOLTIP_PROPS,
} from './shared/chartStyle';
import { ChartCard, ChartMessage, ChartPanel } from './shared/ChartPanel';
import { markerDots } from './shared/markers';
import { useChartPeriod } from '../common/hooks/useChartPeriod';
import { CHART_COLORS } from '../common/constants';
import type { OHLCData } from '../../../types/ohlc';
import { getTodayString } from '../../../utils/dateUtils';
import { formatKrw, formatUsd } from '../../../utils/gainLoss';

interface SymbolData {
  symbol: string;
  purchaseDate: string;
  purchasePrice: number;
  shares: number;
  investmentAmount: number;
  currentPrice: number;
  currentValueKrw: number;
  color: string; // Chart line color
  // 원화 주가 기준 최적 매수 · 매도일
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
  [key: string]: string | number; // Dynamic keys for each symbol's price and portfolio value
}

const EMPTY_CHART_DATA: ChartDataPoint[] = [];
const DEFAULT_PRICE_DOMAIN: [number, number] = [0, 100];
const DEFAULT_PORTFOLIO_DOMAIN: [number, number] = [0, 1000000];

export const CompareSymbolsChart: React.FC<SymbolComparisonChartProps> = ({
  symbols,
  startDate,
  endDate,
}) => {
  const period = useChartPeriod('purchase');
  const { chartPeriod, customStartDate, getStartDateFromPeriod } = period;

  // 차트 기간에 따른 시작일. 조회 키에 들어가 기간을 바꾸면 다시 받음
  const apiStartDate = getStartDateFromPeriod(chartPeriod, startDate, customStartDate);
  const today = endDate || getTodayString();

  // 모든 종목의 가격 · 환율을 받아 차트 데이터 · 축 범위까지 만듦
  const { data, isLoading: loading, error } = useQuery({
    queryKey: ['backtest', 'chart', 'symbols',
      symbols.map((s) => [s.symbol, s.purchaseDate, s.shares, s.investmentAmount]), startDate, apiStartDate, today],
    enabled: symbols.length > 0,
    queryFn: async () => {
      try {
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

            const price = closeOf(item);
            point[`${symbol}_price`] = price;

            // 매수일 이후에만 포트폴리오 가치 계산
            // purchaseDate가 비어있거나 undefined인 경우 startDate를 대체값으로 사용
            const effectivePurchaseDate = symbolData.purchaseDate || startDate;

            if (dateStr >= effectivePurchaseDate) {
              // 해당 날짜의 환율 사용 (각 날짜마다 다른 환율 적용)
              const historicalFxRate = fxRateMap.get(dateStr) || DEFAULT_FX_RATE;
              const portfolioValueKrw = symbolData.shares * price * historicalFxRate;
              point[`${symbol}_portfolio`] = portfolioValueKrw;
            } else {
              // 매수일 이전에는 0으로 설정
              point[`${symbol}_portfolio`] = 0;
            }

            // 산점도를 위한 매수 포인트 표시
            if (dateStr === effectivePurchaseDate) {
              point[`${symbol}_purchasePrice`] = price;
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

        return {
          chartData: chartDataArray,
          priceDomain: calculatedPriceDomain,
          portfolioDomain: calculatedPortfolioDomain,
        };
      } catch (err) {
        console.error('비교 차트 데이터 조회 실패', err);
        throw err;
      }
    },
  });

  const chartData = data?.chartData ?? EMPTY_CHART_DATA;
  const xAxis = dateAxis(chartData.map((d) => d.date));
  const priceDomain = data?.priceDomain ?? DEFAULT_PRICE_DOMAIN;
  const portfolioDomain = data?.portfolioDomain ?? DEFAULT_PORTFOLIO_DOMAIN;
  const errorMessage = error ? '차트 데이터를 가져오는데 실패했습니다.' : null;

  // 종목마다 매수일(또는 그 뒤 첫 거래일)의 주가 · 평가금
  const purchasePoints = symbols.flatMap((symbolData) => {
    const point = chartData.find((d) => d.date >= symbolData.purchaseDate);
    if (!point) return [];
    return [{
      date: point.date,
      price: point[`${symbolData.symbol}_price`] as number,
      portfolio: point[`${symbolData.symbol}_portfolio`] as number,
    }];
  });

  if (loading) {
    return <ChartMessage>차트 데이터 로딩 중...</ChartMessage>;
  }

  if (errorMessage) {
    return <ChartMessage tone="error">{errorMessage}</ChartMessage>;
  }

  if (chartData.length === 0) {
    return <ChartMessage>차트 데이터가 없습니다.</ChartMessage>;
  }

  return (
    <ChartCard title="종목별 비교 차트" period={period}>
      <ChartPanel title="주가 추이 비교" data={chartData} xAxis={xAxis} className="mt-6">
        <YAxis
          domain={priceDomain}
          {...AXIS_PROPS}
          tickFormatter={formatUsdTick}
        />
        <Tooltip
          {...TOOLTIP_PROPS}
          formatter={(value: number | string) => formatUsd(Number(value))}
          labelFormatter={formatTooltipDate}
        />
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
        {markerDots(purchasePoints, 'price', MARKER_COLORS.purchase, 'purchase')}
      </ChartPanel>

      <ChartPanel title="종목별 평가금액 추이" data={chartData} xAxis={xAxis} className="mt-6">
        <YAxis
          domain={portfolioDomain}
          {...AXIS_PROPS}
          tickFormatter={formatKrwTick}
        />
        <Tooltip
          {...TOOLTIP_PROPS}
          formatter={(value: number | string) => formatKrw(Math.round(Number(value)))}
          labelFormatter={formatTooltipDate}
        />

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
        {markerDots(purchasePoints, 'portfolio', MARKER_COLORS.purchase, 'purchase-portfolio')}

        {/* 최적 매수 · 매도 마커 (평가금 위) */}
        {symbols.flatMap((symbolData) =>
          [symbolData.optimalBuyDate, symbolData.optimalSellDate].map((date, i) => {
            const point = date ? chartData.find(d => d.date === date) : undefined;
            if (!point) return null;

            return (
              <ReferenceDot
                key={`${symbolData.symbol}-optimal-${i === 0 ? 'buy' : 'sell'}`}
                x={point.date}
                y={point[`${symbolData.symbol}_portfolio`]}
                {...OPTIMAL_MARKER_PROPS}
                label={{ value: '', position: 'top' }}
              />
            );
          })
        )}
      </ChartPanel>
      <div className="mt-3 p-3 bg-warning-soft/10 border border-warning-soft/40 rounded-lg">
        <p className="text-xs text-tx-1">
          <span className="font-semibold">차트 마커 안내:</span><br/>
          <span className="inline-block w-3 h-3 rounded-full mr-1 align-middle" style={{ backgroundColor: MARKER_COLORS.purchase }}></span> 보라 점 = 실제 매수 시점 |
          <span className="inline-block w-3 h-3 rounded-full mr-1 ml-2 align-middle" style={{ backgroundColor: MARKER_COLORS.optimal }}></span> 금색 점 = 최적 매수 · 매도 시점 (원화로 가장 싸게 사서 가장 비싸게 파는 날)
        </p>
      </div>
    </ChartCard>
  );
};
