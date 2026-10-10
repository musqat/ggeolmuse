import type { ConditionalStrategyRequest } from '@services/api';
import type { BuildResult, RunContext } from '../types';
import { toOptionFields, type BacktestOptions } from '../../shared/backtestOptions';

export interface ConditionalValues extends BacktestOptions {
  startDate: string;
  endDate: string;
  investmentMode: 'TOTAL_BUDGET' | 'PER_PURCHASE';
  totalInvestment: string;
  amountPerPurchase: string;
  maxPurchases: string;
  dropPercentage: string;
}

// 조건부 매수 요청. 검사에 걸리면 alert 문구를 돌려준다
export const buildConditionalRequest = (
  values: ConditionalValues,
  symbol: string,
  ctx: RunContext,
): BuildResult<ConditionalStrategyRequest> => {
  const drop = parseFloat(values.dropPercentage);

  // 투자 모드별 유효성 검사
  if (values.investmentMode === 'TOTAL_BUDGET') {
    const investment = parseFloat(values.totalInvestment);
    const perPurchase = parseFloat(values.amountPerPurchase);
    if (isNaN(investment) || investment <= 0) {
      return { error: '올바른 총 투자금을 입력해주세요.' };
    }
    if (isNaN(perPurchase) || perPurchase <= 0) {
      return { error: '올바른 회당 투자금을 입력해주세요.' };
    }
    if (perPurchase > investment) {
      return { error: '회당 투자금은 총 투자금보다 작아야 합니다.' };
    }
  } else {
    const perPurchase = parseFloat(values.amountPerPurchase);
    const maxCount = parseInt(values.maxPurchases);
    if (isNaN(perPurchase) || perPurchase <= 0) {
      return { error: '올바른 회당 투자금을 입력해주세요.' };
    }
    if (isNaN(maxCount) || maxCount <= 0) {
      return { error: '올바른 최대 횟수를 입력해주세요.' };
    }
  }

  if (isNaN(drop) || drop <= 0 || drop > 100) {
    return { error: '하락률은 0~100 사이여야 합니다.' };
  }

  // 종료일이 비어있으면 오늘
  const effectiveEndDate = values.endDate || ctx.today;
  if (new Date(values.startDate) >= new Date(effectiveEndDate)) {
    return { error: '시작일은 종료일보다 빠른 날짜여야 합니다.' };
  }

  const request: ConditionalStrategyRequest = {
    symbol,
    startDate: values.startDate,
    endDate: effectiveEndDate,
    investmentMode: values.investmentMode,
    dropPercentage: drop / 100,
    ...toOptionFields(values),
    userId: ctx.userId,
  };
  if (values.investmentMode === 'TOTAL_BUDGET') {
    request.totalInvestment = parseFloat(values.totalInvestment);
    request.amountPerPurchase = parseFloat(values.amountPerPurchase);
  } else {
    request.amountPerPurchase = parseFloat(values.amountPerPurchase);
    request.maxPurchases = parseInt(values.maxPurchases);
  }
  return { request };
};
