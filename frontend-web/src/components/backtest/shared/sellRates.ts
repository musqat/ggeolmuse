// 익절 · 손절 퍼센트 칸(빈 값은 끔)을 요청 비율로 바꾸고, 범위 밖이면 문구
export interface SellRates {
  takeProfitRate?: number;
  stopLossRate?: number;
}

export const toSellRates = (
  takeProfit: string | undefined,
  stopLoss: string | undefined,
): { rates: SellRates } | { error: string } => {
  const rates: SellRates = {};
  if (takeProfit?.trim()) {
    const rate = parseFloat(takeProfit);
    if (isNaN(rate) || rate <= 0) return { error: '익절 비율은 0보다 커야 합니다.' };
    rates.takeProfitRate = rate / 100;
  }
  if (stopLoss?.trim()) {
    const rate = parseFloat(stopLoss);
    if (isNaN(rate) || rate <= 0 || rate >= 100) return { error: '손절 비율은 0보다 크고 100보다 작아야 합니다.' };
    rates.stopLossRate = rate / 100;
  }
  return { rates };
};

// 익절 · 손절 가운데 하나라도 적었는지(배당 재투자와 함께 못 씀)
export const hasSellRates = (takeProfit?: string, stopLoss?: string): boolean =>
  Boolean(takeProfit?.trim() || stopLoss?.trim());
