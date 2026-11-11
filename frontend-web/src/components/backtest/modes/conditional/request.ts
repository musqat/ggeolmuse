import type { ConditionalStrategyRequest, PriceDirection } from '@services/api';
import { DIRECTIONS } from '../../shared/priceDirection';
import type { BuildResult, RunContext } from '../types';
import { toOptionFields, type BacktestOptions } from '../../shared/backtestOptions';
import { hasSellRates, toSellRates } from '../../shared/sellRates';
import { hasTrailingStop, toTrailingStop } from '../../shared/trailingStop';
import { hasPartialTakeProfits, toPartialTakeProfits, type TakeProfitStep } from '../../shared/partialTakeProfit';
import { parseShares, type AmountUnitValues } from '../../shared/amountUnit';

export interface ConditionalValues extends BacktestOptions, AmountUnitValues {
  startDate: string;
  endDate: string;
  amountPerPurchase: string;
  totalInvestment: string;
  maxPurchases: string;
  direction: PriceDirection;
  dropPercentage: string;
  takeProfit: string;
  stopLoss: string;
  // 트레일링 스탑 고점 대비 하락 % · 보유의 팔 %(비우면 끔 · 전량)
  trailingStop?: string;
  trailingSell?: string;
  // 부분 익절 단계(비우면 끔)
  partialTakeProfits?: TakeProfitStep[];
}

// 빈 칸은 undefined, 아니면 숫자(못 읽으면 NaN)
const optionalNumber = (value: string) => (value.trim() === '' ? undefined : Number(value));

// 조건부 매수 요청(검사에 걸리면 alert 문구)
export const buildConditionalRequest = (
  values: ConditionalValues,
  symbol: string,
  ctx: RunContext,
): BuildResult<ConditionalStrategyRequest> => {
  const drop = parseFloat(values.dropPercentage);
  const perPurchase = parseFloat(values.amountPerPurchase);
  const shares = parseShares(values.shares);
  const byShares = values.unit === 'SHARES';
  const budget = optionalNumber(values.totalInvestment);
  const maxCount = optionalNumber(values.maxPurchases);

  if (byShares) {
    if (shares === undefined) {
      return { error: '회당 주식 수는 1 ~ 1,000,000 사이의 정수로 입력해주세요.' };
    }
  } else if (isNaN(perPurchase) || perPurchase <= 0) {
    return { error: '올바른 회당 금액을 입력해주세요.' };
  }
  if (budget !== undefined && !(budget > 0)) {
    return { error: '예산은 비우거나 0보다 크게 입력해주세요.' };
  }
  if (!byShares && budget !== undefined && perPurchase > budget) {
    return { error: '회당 금액이 예산보다 큽니다.' };
  }
  if (maxCount !== undefined && !(Number.isInteger(maxCount) && maxCount > 0)) {
    return { error: '최대 횟수는 비우거나 1 이상의 정수로 입력해주세요.' };
  }

  if (isNaN(drop) || drop <= 0 || drop > 100) {
    return { error: `${DIRECTIONS[values.direction].rate}은 0~100 사이여야 합니다.` };
  }

  const sell = toSellRates(values.takeProfit, values.stopLoss);
  if ('error' in sell) {
    return sell;
  }
  const trailing = toTrailingStop(values.trailingStop ?? '', values.trailingSell ?? '');
  if ('error' in trailing) {
    return trailing;
  }
  const partial = toPartialTakeProfits(values.partialTakeProfits ?? []);
  if ('error' in partial) {
    return partial;
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
    direction: values.direction,
    dropPercentage: drop / 100,
    ...(byShares ? { sharesPerPurchase: shares } : { amountPerPurchase: perPurchase }),
    ...(budget !== undefined && { totalInvestment: budget }),
    ...(maxCount !== undefined && { maxPurchases: maxCount }),
    ...toOptionFields(values),
    ...sell.rates,
    ...trailing.fields,
    ...partial.fields,
  };
  // 익절 · 손절 · 트레일링 스탑 · 부분 익절은 배당 재투자와 함께 못 씀
  if (
    hasSellRates(values.takeProfit, values.stopLoss) ||
    hasTrailingStop(values.trailingStop) ||
    hasPartialTakeProfits(values.partialTakeProfits)
  ) {
    request.reinvestDividends = false;
  }
  return { request };
};
