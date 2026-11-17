import type { HoldingResponse } from '../services/api';

// 계좌 하나의 투자금 · 평가금액 · 손익 · 수익률과 현재가가 없어 뺀 종목
export type AccountSummary = {
  totalInvestedAmount: number;
  totalCurrentValue: number;
  totalUnrealizedPnL: number;
  totalReturnRate: number;
  unpricedSymbols: string[];
};

// 보유 목록의 요약(평가금액 · 손익 · 수익률은 현재가 있는 종목만, 투자금은 모든 종목)
export const summarizeHoldings = (holdings: HoldingResponse[]): AccountSummary => {
  let invested = 0;
  let pricedInvested = 0;
  let currentValue = 0;
  const unpricedSymbols: string[] = [];
  for (const holding of holdings) {
    const amount = Number(holding.totalInvestedAmount) || 0;
    const price = Number(holding.currentPrice);
    invested += amount;
    if (price > 0) {
      currentValue += Number(holding.totalQuantity) * price;
      pricedInvested += amount;
    } else if (!unpricedSymbols.includes(holding.symbol)) {
      unpricedSymbols.push(holding.symbol);
    }
  }
  const pnl = currentValue - pricedInvested;
  return {
    totalInvestedAmount: invested,
    totalCurrentValue: currentValue,
    totalUnrealizedPnL: pnl,
    totalReturnRate: pricedInvested > 0 ? Math.round((pnl / pricedInvested) * 10_000) / 100 : 0,
    unpricedSymbols,
  };
};
