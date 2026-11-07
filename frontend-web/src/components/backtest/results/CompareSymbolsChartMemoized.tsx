import React, { useMemo } from 'react';
import { CompareSymbolsChart } from '@components/charts/backtest/CompareSymbolsChart';
import type { ComparisonItem } from '../../../services/api';
import { CHART_COLORS } from '@components/charts/common/constants';

// 비교 항목을 주가 차트 입력으로 바꿈
export const CompareSymbolsChartMemoized: React.FC<{
  items: ComparisonItem[];
  comparePurchaseDate: string;
  compareSaleDate: string;
}> = ({ items, comparePurchaseDate, compareSaleDate }) => {
  const symbolsData = useMemo(
    () =>
      items.map((item, index) => ({
        symbol: item.code,
        purchaseDate: item.additionalData?.purchaseDate || item.purchaseDate || comparePurchaseDate,
        color: CHART_COLORS[index % CHART_COLORS.length],
        optimalBuyDate: item.optimalBuyDate,
        optimalSellDate: item.optimalSellDate,
      })),
    [items, comparePurchaseDate],
  );

  return <CompareSymbolsChart symbols={symbolsData} startDate={comparePurchaseDate} endDate={compareSaleDate || undefined} />;
};
