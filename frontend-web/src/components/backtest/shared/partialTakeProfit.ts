import type { PartialTakeProfit, PartialTakeProfitRequest } from '@services/api';
import { pickValues } from '../settings/snapshot';
import { toRate } from './maBand';

// 부분 익절 한 단계 칸(평균 단가 대비 수익률 % · 보유의 팔 %)
export interface TakeProfitStep {
  gain: string;
  sell: string;
}

export const MAX_TAKE_PROFIT_STEPS = 5;

const COUNT_ERROR = '부분 익절은 5단계까지 넣을 수 있습니다.';
const gainError = (n: number) => `부분 익절 ${n}단계 수익률은 0보다 크고 앞 단계보다 커야 합니다.`;
const sellError = (n: number) => `부분 익절 ${n}단계 팔 비율은 0보다 크고 100 이하여야 합니다.`;

// 퍼센트 칸 → 요청 비율(빈 칸 · 못 읽는 글은 NaN)
export const stepRate = (percent: string) => (percent.trim() ? toRate(Number(percent.trim())) : NaN);

// 부분 익절 단계 칸을 요청 비율로, 단계가 없으면 끔(수익률 · 팔 비율은 요청 비율로 바꾼 값으로 검사)
export const toPartialTakeProfits = (
  steps: TakeProfitStep[],
): { fields: PartialTakeProfitRequest } | { error: string } => {
  if (!steps.length) return { fields: {} };
  if (steps.length > MAX_TAKE_PROFIT_STEPS) return { error: COUNT_ERROR };
  const rates: PartialTakeProfit[] = [];
  for (const [index, step] of steps.entries()) {
    const gainRate = stepRate(step.gain);
    const sellRate = stepRate(step.sell);
    if (!(gainRate > 0) || (index > 0 && !(gainRate > rates[index - 1].gainRate))) return { error: gainError(index + 1) };
    if (!(sellRate > 0 && sellRate <= 1)) return { error: sellError(index + 1) };
    rates.push({ gainRate, sellRate });
  }
  return { fields: { partialTakeProfits: rates } };
};

// 부분 익절 단계를 하나라도 두었는지(배당 재투자와 함께 못 씀)
export const hasPartialTakeProfits = (steps?: TakeProfitStep[]): boolean => Boolean(steps?.length);

const isStep = (value: unknown): value is TakeProfitStep =>
  typeof value === 'object' &&
  value !== null &&
  typeof (value as TakeProfitStep).gain === 'string' &&
  typeof (value as TakeProfitStep).sell === 'string';

// 직전 설정에 저장한 단계(글 칸 둘인 것만, 5단계까지)
export const savedSteps = (saved: unknown): TakeProfitStep[] =>
  pickValues({ partialTakeProfits: [] as unknown[] }, saved)
    .partialTakeProfits.filter(isStep)
    .slice(0, MAX_TAKE_PROFIT_STEPS)
    .map(({ gain, sell }) => ({ gain, sell }));
