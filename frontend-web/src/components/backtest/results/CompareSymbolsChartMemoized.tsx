import React, { useMemo } from 'react';
import { CompareSymbolsChart } from '@components/charts/backtest/CompareSymbolsChart';
import type { ComparisonItem } from '../../../services/api';
import { CHART_COLORS, type OptimalPointsBySymbol } from '../shared/backtestDisplay';

// 무한 리렌더링 방지를 위한 메모이제이션 래퍼 컴포넌트
export const CompareSymbolsChartMemoized: React.FC<{
  items: ComparisonItem[];
  comparePurchaseDate: string;
  compareSaleDate: string;
  onOptimalPointsCalculated: (points: OptimalPointsBySymbol) => void;
}> = ({
  items,
  comparePurchaseDate,
  compareSaleDate,
  onOptimalPointsCalculated,
}) => {
  const symbolsData = useMemo(() => {
    return items.map((item: ComparisonItem, index: number) => {
      // 중첩된 구조에서 추출
      const additionalData = item.additionalData || {};

      const purchasePrice =
        item.averagePrice || additionalData.purchasePrice || 0;
      const investmentAmount =
        item.totalInvested || additionalData.investmentAmount || 0;
      const shares = item.totalShares || additionalData.shares || 0;
      const purchaseDate =
        additionalData.purchaseDate || item.purchaseDate || comparePurchaseDate;
      const currentPrice =
        additionalData.currentPrice || item.currentPrice || 0;

      return {
        symbol: item.symbol || item.name,
        purchaseDate: purchaseDate,
        purchasePrice: purchasePrice,
        shares: shares,
        investmentAmount: investmentAmount,
        currentPrice: currentPrice,
        currentValueKrw: item.currentValueKrw || 0,
        color: CHART_COLORS[index % CHART_COLORS.length],
      };
    });
  }, [items, comparePurchaseDate]);

  return (
    <CompareSymbolsChart
      symbols={symbolsData}
      startDate={comparePurchaseDate}
      endDate={compareSaleDate || undefined}
      onOptimalPointsCalculated={onOptimalPointsCalculated}
    />
  );
};
