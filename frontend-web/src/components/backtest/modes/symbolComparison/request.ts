import type { SymbolComparisonRequest } from '@services/api';
import type { BuildResult, RunContext } from '../types';

export interface SymbolComparisonValues {
  symbols: string[];
  purchaseDate: string;
  saleDate: string;
  investment: string;
  reinvestDividends: boolean;
  tradingFeeRate: string;
  dividendTax: boolean;
  fxMode: 'auto' | 'manual';
  manualPurchaseFxRate: string;
  manualCurrentFxRate: string;
}

// 종목 비교 요청. 검사에 걸리면 alert 문구를 돌려준다
export const buildSymbolComparisonRequest = (
  values: SymbolComparisonValues,
  ctx: RunContext,
): BuildResult<SymbolComparisonRequest> => {
  if (values.symbols.length < 2) {
    return { error: '최소 2개 이상의 종목을 선택해주세요.' };
  }

  const investment = parseFloat(values.investment);
  if (isNaN(investment) || investment <= 0) {
    return { error: '올바른 투자 금액을 입력해주세요.' };
  }

  // 매도일이 비어있으면 오늘
  const effectiveSaleDate = values.saleDate || ctx.today;
  if (values.saleDate && new Date(values.purchaseDate) >= new Date(values.saleDate)) {
    return { error: '시작일은 종료일보다 빠른 날짜여야 합니다.' };
  }

  const request: SymbolComparisonRequest = {
    symbols: values.symbols,
    startDate: values.purchaseDate,
    endDate: effectiveSaleDate,
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
