import type { DcaStrategyRequest } from '@services/api';
import type { BuildResult, RunContext } from '../types';
import { toOptionFields, type BacktestOptions } from '../../shared/backtestOptions';
import { parseShares, type AmountUnitValues } from '../../shared/amountUnit';
import { dayOfMonth, isMonthly, parseMonths, toFrequency, toWeekday, type DcaCadence } from '../../shared/dcaSchedule';

export interface DcaValues extends BacktestOptions, AmountUnitValues {
  startDate: string;
  endDate: string;
  amountPerPurchase: string;
  cadence: DcaCadence;
  // N개월마다의 개월 수
  months: string;
  firstPurchaseDate: string;
}

// 적립식 요청(검사에 걸리면 alert 문구), 매달 투자일은 첫 매수일의 며칠
export const buildDcaRequest = (
  values: DcaValues,
  symbol: string,
  ctx: RunContext,
): BuildResult<DcaStrategyRequest> => {
  const amount = parseFloat(values.amountPerPurchase);
  const shares = parseShares(values.shares);
  const months = values.cadence === 'MONTHS' ? parseMonths(values.months) : 1;

  if (values.unit === 'SHARES') {
    if (shares === undefined) {
      return { error: '회당 주식 수는 1 이상의 정수로 입력해주세요.' };
    }
  } else if (isNaN(amount) || amount <= 0) {
    return { error: '올바른 회당 금액을 입력해주세요.' };
  }
  if (months === undefined) {
    return { error: '개월 수는 1~12 사이의 정수로 입력해주세요.' };
  }

  // 종료일이 비어있으면 오늘
  const effectiveEndDate = values.endDate || ctx.today;
  if (new Date(values.startDate) >= new Date(effectiveEndDate)) {
    return { error: '시작일은 종료일보다 빠른 날짜여야 합니다.' };
  }
  const first = values.firstPurchaseDate;
  if (first && (first < values.startDate || first > effectiveEndDate)) {
    return { error: '첫 매수일은 시작일과 종료일 사이로 골라주세요.' };
  }
  const base = first || values.startDate;
  // 매주는 주말 첫 매수일을 다음 월요일로(종료일을 넘으면 그대로)
  const weekday = toWeekday(base);
  const firstPurchaseDate = values.cadence === 'WEEKLY' && weekday <= effectiveEndDate ? weekday : first;

  const request: DcaStrategyRequest = {
    symbol,
    startDate: values.startDate,
    endDate: effectiveEndDate,
    ...(values.unit === 'SHARES' ? { sharesPerPurchase: shares } : { amountPerPurchase: amount }),
    frequency: toFrequency(values.cadence),
    ...(firstPurchaseDate && { firstPurchaseDate }),
    ...(isMonthly(values.cadence) && {
      purchaseDay: dayOfMonth(base),
      investmentInterval: months,
    }),
    ...toOptionFields(values),
  };
  return { request };
};
