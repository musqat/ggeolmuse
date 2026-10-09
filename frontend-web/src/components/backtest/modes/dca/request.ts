import type { DcaStrategyRequest } from '@services/api';
import type { BuildResult, RunContext } from '../types';

export interface DcaValues {
  startDate: string;
  endDate: string;
  monthlyAmount: string;
  purchaseDay: string;
  investmentInterval: string;
  reinvestDividends: boolean;
  tradingFeeRate: string;
  dividendTax: boolean;
  fxMode: 'auto' | 'manual';
  manualPurchaseFxRate: string;
  manualCurrentFxRate: string;
}

// 적립식 요청. 검사에 걸리면 alert 문구를 돌려준다
export const buildDcaRequest = (
  values: DcaValues,
  symbol: string,
  ctx: RunContext,
): BuildResult<DcaStrategyRequest> => {
  const monthly = parseFloat(values.monthlyAmount);
  const day = parseInt(values.purchaseDay);
  const interval = parseInt(values.investmentInterval);

  if (isNaN(monthly) || monthly <= 0) {
    return { error: '올바른 월 투자 금액을 입력해주세요.' };
  }
  if (isNaN(day) || day < 1 || day > 28) {
    return { error: '투자일은 1~28 사이여야 합니다.' };
  }

  // 종료일이 비어있으면 오늘
  const effectiveEndDate = values.endDate || ctx.today;
  if (new Date(values.startDate) >= new Date(effectiveEndDate)) {
    return { error: '시작일은 종료일보다 빠른 날짜여야 합니다.' };
  }

  const request: DcaStrategyRequest = {
    symbol,
    startDate: values.startDate,
    endDate: effectiveEndDate,
    monthlyAmount: monthly,
    purchaseDay: day,
    investmentInterval: interval,
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
