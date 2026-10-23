import React, { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
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
import type { ComparisonDetail } from '../../../services/api';
import { DEFAULT_FX_RATE, fetchFxRates } from './shared/fxRates';
import { closeOf, fetchSymbolPrices } from './shared/prices';
import {
  AXIS_PROPS,
  CHART_HEIGHT,
  CHART_MARGIN,
  dateAxis,
  formatKrwTick,
  formatTooltipDate,
  formatUsdTick,
  GRID_PROPS,
  MARKER_PROPS,
  TOOLTIP_PROPS,
} from './shared/chartStyle';
import type { OHLCData } from '../../../types/ohlc';
import { useChartPeriod } from '../common/hooks/useChartPeriod';
import { ChartPeriodSelector } from '../common/components/ChartPeriodSelector';
import { CHART_COLORS } from '../common/constants';
import { getTodayString } from '../../../utils/dateUtils';

// 전략 이름이 그대로 컬럼이 된다 (`${name}_portfolio`). 키를 미리 적을 수 없어
// 인덱스 시그니처를 쓴다.
interface ChartRow {
  date: string;
  [column: string]: string | number;
}

interface PurchasePoint {
  date: string;
  strategyName: string;
  strategyIdx: number;
  stockPrice: number;
}

interface StrategyItem {
  name: string;
  totalInvested?: number | null;
  currentValueKrw?: number | null;
  // 적립식 · 조건부는 StrategyResponse, 단순 매수는 SimulationResponse 칸이 온다
  additionalData?: ComparisonDetail | null;
}

interface StockPriceWithStrategyChartProps {
  strategies: StrategyItem[];
  strategyNames: Record<string, string>;
}

const EMPTY_PRICES: OHLCData[] = [];
const EMPTY_FX_RATES = new Map<string, number>();

export const CompareStrategiesChart: React.FC<StockPriceWithStrategyChartProps> = ({
  strategies,
  strategyNames
}) => {
  // 공통 차트 기간 훅 사용
  const {
    chartPeriod,
    customStartDate,
    setChartPeriod,
    setCustomStartDate,
    getStartDateFromPeriod
  } = useChartPeriod('purchase');

  // 첫 전략의 종목 · 기간으로 가격을 받음
  const first = strategies[0]?.additionalData;
  const symbol = first?.symbol;
  const originalStartDate = first?.startDate || first?.purchaseDate;
  const endDate = first?.endDate || first?.currentDate;
  const apiStartDate = originalStartDate ? getStartDateFromPeriod(chartPeriod, originalStartDate, customStartDate) : undefined;

  // 평가금액은 다른 결과 차트처럼 그날 환율로 원화 환산. 가격을 못 받으면 차트는 비어 보이고 이유를 남김
  const { data: fetched, isLoading: loading } = useQuery({
    queryKey: ['backtest', 'chart', 'strategies', symbol, apiStartDate, endDate],
    enabled: Boolean(symbol && apiStartDate),
    queryFn: async () => {
      try {
        const prices = await fetchSymbolPrices<OHLCData>(symbol!, apiStartDate!, endDate || getTodayString());
        const fxRates = await fetchFxRates(prices.map((item) => item.date));
        return { prices, fxRates };
      } catch (error) {
        console.warn('비교 차트의 가격 데이터를 불러오지 못했습니다', symbol, error);
        return { prices: EMPTY_PRICES, fxRates: EMPTY_FX_RATES };
      }
    },
  });
  const priceData = fetched?.prices ?? EMPTY_PRICES;
  const fxRates = fetched?.fxRates ?? EMPTY_FX_RATES;

  // 매수 포인트를 포함한 차트 데이터 계산
  const { priceChartData, portfolioChartData, purchasePoints } = useMemo(() => {
    if (priceData.length === 0 || strategies.length === 0) {
      return { priceChartData: [], portfolioChartData: [], purchasePoints: [] };
    }

    const priceDataMap = new Map<string, ChartRow>();
    const portfolioDataMap = new Map<string, ChartRow>();
    const allPurchasePoints: PurchasePoint[] = [];
    const availableDates = priceData.map((item) => item.date).sort();

    // 가격 데이터로 초기화
    priceData.forEach((price) => {
      priceDataMap.set(price.date, {
        date: price.date,
        stockPrice: closeOf(price)
      });
      portfolioDataMap.set(price.date, {
        date: price.date
      });
    });

    // 각 전략 처리
    strategies.forEach((strategy, strategyIdx) => {
      if (!strategy.additionalData) return;

      const data = strategy.additionalData;
      const columnName = strategy.name;

      // SIMPLE 전략 처리
      if (data.purchaseDate && !data.transactions) {
        const purchaseDate = data.purchaseDate;
        let tradingDate = purchaseDate;

        if (!availableDates.includes(purchaseDate)) {
          const nextTradingDay = availableDates.find(d => d > purchaseDate);
          if (nextTradingDay) tradingDate = nextTradingDay;
        }

        const priceAtPurchase = priceData.find((p) => p.date === tradingDate);
        if (priceAtPurchase) {
          allPurchasePoints.push({
            date: tradingDate,
            strategyName: strategy.name,
            strategyIdx: strategyIdx,
            stockPrice: closeOf(priceAtPurchase)
          });
        }

        const shares = data.shares || 0;
        const investmentAmount = data.investmentAmount || strategy.totalInvested || 0;

        priceData.forEach((price) => {
          if (price.date >= tradingDate) {
            const portfolioValueUSD = shares * closeOf(price);
            const portfolioValueKRW = portfolioValueUSD * (fxRates.get(price.date) || DEFAULT_FX_RATE);

            const existing = portfolioDataMap.get(price.date);
            if (existing) {
              existing[`${columnName}_portfolio`] = portfolioValueKRW;
              existing[`${columnName}_invested`] = investmentAmount;
            }
          }
        });

        return;
      }

      // DCA/CONDITIONAL 전략 처리
      if (!data.transactions) return;

      let cumulativeShares = 0;
      let cumulativeInvestment = 0;
      const sortedTransactions = [...data.transactions].sort((a, b) =>
        (a.actualDate || a.date).localeCompare(b.actualDate || b.date)
      );

      // 매수 포인트 수집
      sortedTransactions.forEach(tx => {
        const txDate = tx.actualDate || tx.date;
        let tradingDate = txDate;

        if (!availableDates.includes(txDate)) {
          const nextTradingDay = availableDates.find(d => d > txDate);
          if (nextTradingDay) tradingDate = nextTradingDay;
        }

        const priceAtPurchase = priceData.find((p) => p.date === tradingDate);
        if (priceAtPurchase) {
          allPurchasePoints.push({
            date: tradingDate,
            strategyName: strategy.name,
            strategyIdx: strategyIdx,
            stockPrice: closeOf(priceAtPurchase)
          });
        }
      });

      // 포트폴리오 가치 계산
      priceData.forEach((price, priceIndex) => {
        sortedTransactions.forEach(tx => {
          const txDate = tx.actualDate || tx.date;
          if (txDate <= price.date && txDate > (priceData[priceIndex - 1]?.date || '')) {
            cumulativeShares += tx.shares;
            cumulativeInvestment += tx.amount;
          }
        });

        if (cumulativeShares > 0) {
          const portfolioValueUSD = cumulativeShares * closeOf(price);
          const portfolioValueKRW = portfolioValueUSD * (fxRates.get(price.date) || DEFAULT_FX_RATE);

          const existing = portfolioDataMap.get(price.date);
          if (existing) {
            existing[`${columnName}_portfolio`] = portfolioValueKRW;
            existing[`${columnName}_invested`] = cumulativeInvestment;
          }
        }
      });
    });

    return {
      priceChartData: Array.from(priceDataMap.values()).sort((a, b) => a.date.localeCompare(b.date)),
      portfolioChartData: Array.from(portfolioDataMap.values()).sort((a, b) => a.date.localeCompare(b.date)),
      purchasePoints: allPurchasePoints
    };
  }, [priceData, fxRates, strategies]);

  const xAxis = dateAxis(priceChartData.map((d) => d.date));

  if (loading) {
    return (
      <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6">
        <h3 className="text-lg font-semibold text-tx-1 mb-4">전략별 비교 차트</h3>
        <div className="flex items-center justify-center h-80">
          <div className="text-tx-2">데이터 로딩 중...</div>
        </div>
      </div>
    );
  }

  if (priceChartData.length === 0) {
    return null;
  }

  return (
    <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6 space-y-6">
      <div className="space-y-4">
        <h3 className="text-lg font-semibold text-tx-1">전략별 비교 차트</h3>
        <ChartPeriodSelector
          chartPeriod={chartPeriod}
          customStartDate={customStartDate}
          onPeriodChange={setChartPeriod}
          onCustomDateChange={setCustomStartDate}
        />
      </div>

      {/* 주가 추이 비교 */}
      <div>
        <h4 className="text-sm font-medium text-tx-1 mb-3">주가 추이 비교</h4>
        <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
          <LineChart data={priceChartData} margin={CHART_MARGIN}>
            <CartesianGrid {...GRID_PROPS} />
            <XAxis
              dataKey="date"
              {...AXIS_PROPS}
              {...xAxis}
            />
            <YAxis
              {...AXIS_PROPS}
              tickFormatter={formatUsdTick}
            />
            <Tooltip
              {...TOOLTIP_PROPS}
              formatter={(value: number | string, name: string) => {
                if (name === '주가') {
                  return [`$${Number(value).toFixed(2)}`, '주가'];
                }
                return [value, name];
              }}
              labelFormatter={formatTooltipDate}
            />
            <Legend />

            {/* 주가 라인 */}
            <Line
              type="monotone"
              dataKey="stockPrice"
              stroke="rgb(var(--text-2))"
              strokeWidth={2}
              dot={false}
              name="주가"
              isAnimationActive={false}
            />

            {/* 매수 포인트 마커 (전략별 색상) */}
            {purchasePoints.map((point, index) => (
              <ReferenceDot
                key={`purchase-${index}`}
                x={point.date}
                y={point.stockPrice}
                {...MARKER_PROPS}
                fill={CHART_COLORS[point.strategyIdx % CHART_COLORS.length]}
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>

      {/* 전략별 평가금액 추이 */}
      <div>
        <h4 className="text-sm font-medium text-tx-1 mb-3">전략별 평가금액 추이</h4>
        <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
          <LineChart data={portfolioChartData} margin={CHART_MARGIN}>
            <CartesianGrid {...GRID_PROPS} />
            <XAxis
              dataKey="date"
              {...AXIS_PROPS}
              {...xAxis}
            />
            <YAxis
              {...AXIS_PROPS}
              tickFormatter={formatKrwTick}
            />
            <Tooltip
              {...TOOLTIP_PROPS}
              formatter={(value: number | string, name: string) => {
                if (name.endsWith('_portfolio')) {
                  const strategyName = name.replace('_portfolio', '');
                  return [`₩${Math.floor(Number(value)).toLocaleString()}`, `${strategyNames[strategyName] || strategyName}`];
                }
                if (name.endsWith('_invested')) {
                  const strategyName = name.replace('_invested', '');
                  return [`₩${Math.floor(Number(value)).toLocaleString()}`, `${strategyNames[strategyName] || strategyName} (투자금)`];
                }
                return [value, name];
              }}
              labelFormatter={formatTooltipDate}
            />
            <Legend
              formatter={(value) => {
                if (value.endsWith('_portfolio')) {
                  const strategyName = value.replace('_portfolio', '');
                  return `${strategyNames[strategyName] || strategyName}`;
                }
                return value;
              }}
            />

            {/* 투자금 라인 (점선). 범례에는 평가금액 선만 둔다 */}
            {strategies.map((strategy, index) => (
              <Line
                key={`${strategy.name}_invested`}
                type="monotone"
                dataKey={`${strategy.name}_invested`}
                stroke={CHART_COLORS[index % CHART_COLORS.length]}
                strokeWidth={2}
                dot={false}
                strokeDasharray="5 5"
                name={`${strategy.name}_invested`}
                legendType="none"
                isAnimationActive={false}
              />
            ))}

            {/* 포트폴리오 가치 라인 (실선) */}
            {strategies.map((strategy, index) => (
              <Line
                key={`${strategy.name}_portfolio`}
                type="monotone"
                dataKey={`${strategy.name}_portfolio`}
                stroke={CHART_COLORS[index % CHART_COLORS.length]}
                strokeWidth={2}
                dot={false}
                name={`${strategy.name}_portfolio`}
                isAnimationActive={false}
              />
            ))}

            {/* 매수 포인트 마커 (전략 색상) */}
            {purchasePoints.map((point, index) => {
              const dataPoint = portfolioChartData.find(d => d.date === point.date);
              if (!dataPoint) return null;

              const portfolioValue = dataPoint[`${point.strategyName}_portfolio`];
              if (!portfolioValue) return null;

              return (
                <ReferenceDot
                  key={`purchase-portfolio-${index}`}
                  x={point.date}
                  y={portfolioValue}
                  {...MARKER_PROPS}
                  fill={CHART_COLORS[point.strategyIdx % CHART_COLORS.length]}
                />
              );
            })}
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};
