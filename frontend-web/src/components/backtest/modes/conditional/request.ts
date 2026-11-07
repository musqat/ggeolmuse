import type { ConditionalStrategyRequest, PriceDirection } from '@services/api';
import { DIRECTIONS } from '../../shared/priceDirection';
import type { BuildResult, RunContext } from '../types';
import { toOptionFields, type BacktestOptions } from '../../shared/backtestOptions';
import { hasSellRates, toSellRates } from '../../shared/sellRates';

export interface ConditionalValues extends BacktestOptions {
  startDate: string;
  endDate: string;
  investmentMode: 'TOTAL_BUDGET' | 'PER_PURCHASE';
  totalInvestment: string;
  amountPerPurchase: string;
  maxPurchases: string;
  direction: PriceDirection;
  dropPercentage: string;
  takeProfit: string;
  stopLoss: string;
}

// 조건부 매수 요청(검사에 걸리면 alert 문구)
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
    return { error: `${DIRECTIONS[values.direction].rate}은 0~100 사이여야 합니다.` };
  }

  const sell = toSellRates(values.takeProfit, values.stopLoss);
  if ('error' in sell) {
    return sell;
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
    direction: values.direction,
    dropPercentage: drop / 100,
    ...toOptionFields(values),
    ...sell.rates,
  };
  // 익절 · 손절은 배당 재투자와 함께 못 씀
  if (hasSellRates(values.takeProfit, values.stopLoss)) {
    request.reinvestDividends = false;
  }
  if (values.investmentMode === 'TOTAL_BUDGET') {
    request.totalInvestment = parseFloat(values.totalInvestment);
    request.amountPerPurchase = parseFloat(values.amountPerPurchase);
  } else {
    request.amountPerPurchase = parseFloat(values.amountPerPurchase);
    request.maxPurchases = parseInt(values.maxPurchases);
  }
  return { request };
};
