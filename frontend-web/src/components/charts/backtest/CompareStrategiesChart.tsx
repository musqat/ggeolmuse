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
import { CHART_COLORS } from '../common/constants';

interface StrategyItem {
  name: string;
  totalInvested: number;
  currentValueKrw: number;
  additionalData?: {
    // StrategyResponse 필드
    symbol?: string;
    startDate?: string;
    endDate?: string;
    transactions?: Array<{
      date: string;
      actualDate?: string;
      price: number;
      shares: number;
      amount: number;
      fxRate: number;
    }>;
    // SimulationResponse 필드 (SIMPLE 전략용)
    purchaseDate?: string;
    currentDate?: string;
    investmentAmount?: number;
    purchasePrice?: number;
    shares?: number;
    purchaseFxRate?: number;
  };
}

interface StockPriceWithStrategyChartProps {
  strategies: StrategyItem[];
  strategyNames: Record<string, string>;
}

export const CompareStrategiesChart: React.FC<StockPriceWithStrategyChartProps> = ({
  strategies,
  strategyNames
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

  // 차트 기간에 따라 가격 데이터 조회
  useEffect(() => {
    const fetchPriceData = async () => {
      if (strategies.length === 0) return;

      const firstStrategy = strategies[0];
      if (!firstStrategy.additionalData) {
        setLoading(false);
        return;
      }

      const data = firstStrategy.additionalData;
      const symbol = data.symbol;
      const originalStartDate = data.startDate || data.purchaseDate;
      const endDate = data.endDate || data.currentDate;

      if (!symbol || !originalStartDate) {
        setLoading(false);
        return;
      }

      try {
        setLoading(true);

        // 공통 훅을 사용하여 시작일 계산
        const apiStartDate = getStartDateFromPeriod(chartPeriod, originalStartDate, customStartDate);

        const response = await stockApi.getOHLCData(
          symbol,
          apiStartDate,
          endDate || new Date().toISOString().split('T')[0]
        );
        setPriceData(response.data || []);
      } catch (error) {
      } finally {
        setLoading(false);
      }
    };

    fetchPriceData();
  }, [strategies, chartPeriod, customStartDate]);

  // 매수 포인트를 포함한 차트 데이터 계산
  const { priceChartData, portfolioChartData, purchasePoints } = useMemo(() => {
    if (priceData.length === 0 || strategies.length === 0) {
      return { priceChartData: [], portfolioChartData: [], purchasePoints: [] };
    }

    const priceDataMap = new Map<string, any>();
    const portfolioDataMap = new Map<string, any>();
    const allPurchasePoints: any[] = [];
    const availableDates = priceData.map((item: any) => item.date).sort();

    // 가격 데이터로 초기화
    priceData.forEach((price: any) => {
      const adjustedPrice = price.adjustedClose || price.closePrice;
      priceDataMap.set(price.date, {
        date: price.date,
        stockPrice: adjustedPrice
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

        const priceAtPurchase = priceData.find((p: any) => p.date === tradingDate);
        if (priceAtPurchase) {
          const adjustedPrice = priceAtPurchase.adjustedClose || priceAtPurchase.closePrice;
          allPurchasePoints.push({
            date: tradingDate,
            strategyName: strategy.name,
            strategyIdx: strategyIdx,
            stockPrice: adjustedPrice
          });
        }

        const shares = data.shares || 0;
        const investmentAmount = data.investmentAmount || strategy.totalInvested || 0;
        const fxRate = data.purchaseFxRate || 1300;

        priceData.forEach((price: any) => {
          if (price.date >= tradingDate) {
            const adjustedPrice = price.adjustedClose || price.closePrice;
            const portfolioValueUSD = shares * adjustedPrice;
            const portfolioValueKRW = portfolioValueUSD * fxRate;

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

        const priceAtPurchase = priceData.find((p: any) => p.date === tradingDate);
        if (priceAtPurchase) {
          const adjustedPrice = priceAtPurchase.adjustedClose || priceAtPurchase.closePrice;
          allPurchasePoints.push({
            date: tradingDate,
            strategyName: strategy.name,
            strategyIdx: strategyIdx,
            stockPrice: adjustedPrice
          });
        }
      });

      // 포트폴리오 가치 계산
      priceData.forEach((price: any, priceIndex: any) => {
        sortedTransactions.forEach(tx => {
          const txDate = tx.actualDate || tx.date;
          if (txDate <= price.date && txDate > (priceData[priceIndex - 1]?.date || '')) {
            cumulativeShares += tx.shares;
            cumulativeInvestment += tx.amount;
          }
        });

        if (cumulativeShares > 0) {
          const adjustedPrice = price.adjustedClose || price.closePrice;
          const portfolioValueUSD = cumulativeShares * adjustedPrice;
          const avgFxRate = data.transactions!.length > 0
            ? data.transactions!.reduce((sum, tx) => sum + tx.fxRate, 0) / data.transactions!.length
            : 1300;
          const portfolioValueKRW = portfolioValueUSD * avgFxRate;

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
  }, [priceData, strategies]);

  if (loading) {
    return (
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">전략별 비교 차트</h3>
        <div className="flex items-center justify-center h-80">
          <div className="text-gray-500">데이터 로딩 중...</div>
        </div>
      </div>
    );
  }

  if (priceChartData.length === 0) {
    return null;
  }

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 space-y-6">
      <div className="space-y-4">
        <h3 className="text-lg font-semibold text-gray-900">전략별 비교 차트</h3>
        <ChartPeriodSelector
          chartPeriod={chartPeriod}
          customStartDate={customStartDate}
          showCustomInput={showCustomInput}
          onPeriodChange={setChartPeriod}
          onCustomDateChange={setCustomStartDate}
        />
      </div>

      {/* 주가 추이 비교 */}
      <div>
        <h4 className="text-sm font-medium text-gray-700 mb-3">주가 추이 비교</h4>
        <ResponsiveContainer width="100%" height={300}>
          <LineChart data={priceChartData} margin={{ top: 5, right: 30, left: 20, bottom: 5 }}>
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
              tickFormatter={(value) => `$${value.toFixed(0)}`}
            />
            <Tooltip
              contentStyle={{ backgroundColor: 'rgba(255, 255, 255, 0.95)', border: '1px solid #ccc' }}
              formatter={(value: any, name: any) => {
                if (name === 'stockPrice') {
                  return [`$${Number(value).toFixed(2)}`, '주가'];
                }
                return [value, name];
              }}
              labelFormatter={(label) => `날짜: ${label}`}
            />
            <Legend />

            {/* 주가 라인 */}
            <Line
              type="monotone"
              dataKey="stockPrice"
              stroke="#374151"
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
                r={4}
                fill={CHART_COLORS[point.strategyIdx % CHART_COLORS.length]}
                stroke="#fff"
                strokeWidth={2}
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>

      {/* 전략별 평가금액 추이 */}
      <div>
        <h4 className="text-sm font-medium text-gray-700 mb-3">전략별 평가금액 추이</h4>
        <ResponsiveContainer width="100%" height={300}>
          <LineChart data={portfolioChartData} margin={{ top: 5, right: 30, left: 20, bottom: 5 }}>
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
            />
            <Tooltip
              contentStyle={{ backgroundColor: 'rgba(255, 255, 255, 0.95)', border: '1px solid #ccc' }}
              formatter={(value: any, name: string) => {
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
              labelFormatter={(label) => `날짜: ${label}`}
            />
            <Legend
              formatter={(value) => {
                if (value.endsWith('_portfolio')) {
                  const strategyName = value.replace('_portfolio', '');
                  return `${strategyNames[strategyName] || strategyName}`;
                }
                if (value.endsWith('_invested')) {
                  return null; // Hide invested lines from legend
                }
                return value;
              }}
            />

            {/* 투자금 라인 (점선) */}
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
                  r={4}
                  fill={CHART_COLORS[point.strategyIdx % CHART_COLORS.length]}
                  stroke="#fff"
                  strokeWidth={2}
                />
              );
            })}
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};
