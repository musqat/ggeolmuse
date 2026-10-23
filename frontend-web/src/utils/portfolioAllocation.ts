import type { PieChartData } from '../components/charts/portfolio/PortfolioPieChart';
import type { AccountBalance, HoldingResponse, PortfolioSummaryResponse } from '../services/api';

// 자산 구성 파이 · 자산 배분 목록 색(index.css), 금액 큰 순으로 다섯 자리 · 여섯째부터 회색(파이에서도 ETC 로 묶임)
const ALLOCATION_COLORS = ['var(--alloc-1)', 'var(--alloc-2)', 'var(--alloc-3)', 'var(--alloc-4)', 'var(--alloc-5)'];
export const ALLOCATION_ETC_COLOR = 'var(--alloc-etc)';

// 주식 · USD 현금 · KRW 현금(USD 환산)을 금액 큰 순으로 세운 파이 항목(그 순서로 색)
export const allocationItems = (
  holdings: HoldingResponse[],
  balance: AccountBalance | undefined,
  exchangeRate: number,
): PieChartData[] =>
  [
    ...holdings.map((holding) => ({
      symbol: holding.symbol,
      value: holding.currentValue || holding.totalInvestedAmount,
      color: '',
      quantity: holding.totalQuantity,
      currentPrice: holding.currentPrice,
    })),
    ...(balance && Number(balance.balanceUsd) > 0
      ? [{ symbol: 'USD 현금', value: Number(balance.balanceUsd), color: '' }]
      : []),
    ...(balance && Number(balance.balanceKrw) > 0
      ? [{ symbol: 'KRW 현금', value: Number(balance.balanceKrw) / exchangeRate, color: '' }]
      : []),
  ]
    .sort((a, b) => b.value - a.value)
    .map((item, index) => ({ ...item, color: ALLOCATION_COLORS[index] ?? ALLOCATION_ETC_COLOR }));

// 총자산(USD) = 주식 평가액 + USD 현금 + KRW 현금 ÷ 환율
export const totalAssetsUsd = (
  summary: PortfolioSummaryResponse | null | undefined,
  balance: AccountBalance | undefined,
  exchangeRate: number,
): number =>
  (summary?.totalCurrentValue || 0)
  + (Number(balance?.balanceUsd) || 0)
  + (Number(balance?.balanceKrw) || 0) / exchangeRate;
