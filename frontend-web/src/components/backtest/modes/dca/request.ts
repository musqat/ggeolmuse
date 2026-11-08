import type { DcaStrategyRequest } from '@services/api';
import type { BuildResult, RunContext } from '../types';
import { toOptionFields, type BacktestOptions } from '../../shared/backtestOptions';
import { parseShares, type AmountUnitValues } from '../../shared/amountUnit';
import { dayOfMonth, parseMonths, resolveFirstPurchase, scheduleFields, type DcaCadence } from '../../shared/dcaSchedule';

export interface DcaValues extends BacktestOptions, AmountUnitValues {
  startDate: string;
  endDate: string;
  amountPerPurchase: string;
  cadence: DcaCadence;
  // N개월마다의 개월 수
  months: string;
  // 고른 첫 매수일과 고를 때의 시작일(비면 시작일을 따라감)
  firstPurchaseDate: string;
  firstPickedStart: string;
  // 주식 수 단위의 예산(비면 제한 없음)
  budget: string;
}

// 적립식 요청(검사에 걸리면 alert 문구), 매달 투자일은 고른 첫 매수일(비우면 시작일)의 며칠
export const buildDcaRequest = (
  values: DcaValues,
  symbol: string,
  ctx: RunContext,
): BuildResult<DcaStrategyRequest> => {
  const amount = parseFloat(values.amountPerPurchase);
  const shares = parseShares(values.shares);
  const months = values.cadence === 'MONTHS' ? parseMonths(values.months) : 1;
  const byShares = values.unit === 'SHARES';
  const budget = byShares && values.budget.trim() ? parseFloat(values.budget) : undefined;

  if (byShares) {
    if (shares === undefined) {
      return { error: '회당 주식 수는 1 이상의 정수로 입력해주세요.' };
    }
  } else if (isNaN(amount) || amount <= 0) {
    return { error: '올바른 회당 금액을 입력해주세요.' };
  }
  if (months === undefined) {
    return { error: '개월 수는 1~12 사이의 정수로 입력해주세요.' };
  }
  if (budget !== undefined && !(budget > 0)) {
    return { error: '예산은 비우거나 0보다 크게 입력해주세요.' };
  }

  // 종료일이 비어있으면 오늘
  const effectiveEndDate = values.endDate || ctx.today;
  if (new Date(values.startDate) >= new Date(effectiveEndDate)) {
    return { error: '시작일은 종료일보다 빠른 날짜여야 합니다.' };
  }
  const picked = values.firstPurchaseDate;
  const first = resolveFirstPurchase(values.startDate, values.cadence, { date: picked, start: values.firstPickedStart });
  if (first > effectiveEndDate) {
    return { error: '첫 매수일은 시작일과 종료일 사이로 골라주세요.' };
  }

  const request: DcaStrategyRequest = {
    symbol,
    startDate: values.startDate,
    endDate: effectiveEndDate,
    ...(byShares ? { sharesPerPurchase: shares } : { amountPerPurchase: amount }),
    ...scheduleFields({
      cadence: values.cadence,
      months,
      firstPurchaseDate: first,
      monthDay: picked ? dayOfMonth(picked) : undefined,
      startDate: values.startDate,
      endDate: effectiveEndDate,
    }),
    ...(budget !== undefined && { totalInvestmentLimit: budget }),
    ...toOptionFields(values),
  };
  return { request };
};
