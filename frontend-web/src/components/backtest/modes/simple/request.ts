import type { SimulationRequest } from '@services/api';
import type { BuildResult, RunContext } from '../types';

export interface SimpleValues {
  purchaseDate: string;
  saleDate: string;
  initialInvestment: string;
  reinvestDividends: boolean;
  tradingFeeRate: string;
  dividendTax: boolean;
  fxMode: 'auto' | 'manual';
  manualPurchaseFxRate: string;
  manualCurrentFxRate: string;
}

// 단순 매수 요청. 검사에 걸리면 alert 문구를 돌려준다
export const buildSimpleRequest = (
  values: SimpleValues,
  symbol: string,
  ctx: RunContext,
): BuildResult<SimulationRequest> => {
  const investment = parseFloat(values.initialInvestment);
  if (isNaN(investment) || investment <= 0) {
    return { error: '올바른 투자 금액을 입력해주세요.' };
  }
  if (investment < 100000) {
    return { error: '최소 10만원 이상 투자해주세요. (미국 주식 1주 구매를 위해 약 30만원 권장)' };
  }
  if (new Date(values.purchaseDate) >= ctx.now) {
    return { error: '매수일은 과거 날짜여야 합니다.' };
  }

  // 매도일이 비어있으면 오늘
  const effectiveSaleDate = values.saleDate || ctx.today;
  if (values.saleDate && new Date(values.purchaseDate) >= new Date(values.saleDate)) {
    return { error: '시작일은 종료일보다 빠른 날짜여야 합니다.' };
  }

  const request: SimulationRequest = {
    symbol,
    purchaseDate: values.purchaseDate,
    saleDate: effectiveSaleDate,
    investmentAmount: investment,
    reinvestDividends: values.reinvestDividends,
    tradingFeeRate: parseFloat(values.tradingFeeRate) / 100,
    dividendTaxRate: values.dividendTax ? 0.15 : 0,
    userId: ctx.userId,
  };
  if (values.fxMode === 'manual') {
    request.purchaseFxRate = parseFloat(values.manualPurchaseFxRate);
    request.currentFxRate = parseFloat(values.manualCurrentFxRate);
  }
  return { request };
};
