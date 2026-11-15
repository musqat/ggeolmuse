import { countRounds, type DcaCadence } from './dcaSchedule';
import { formatNumber } from '../../../utils/format';

// 적립 · 매수 회차 × 기간 평일 수 상한(매일 적립 4,000회 · 약 15년)
export const MAX_ROUND_LOAD = 4000 * 4000;
// 전략 비교 칩 회차 × 기간 평일 수 합 상한(칩 하나 상한의 3배)
export const MAX_COMPARISON_ROUND_LOAD = 3 * MAX_ROUND_LOAD;

const WEEKDAYS_PER_YEAR = (365.25 * 5) / 7;
const WEEKS_PER_YEAR = 365.25 / 7;

interface DcaPlan {
  cadence: DcaCadence;
  // N개월마다의 개월 수
  months: number;
  first: string;
  end: string;
  // 고른 첫 매수일의 며칠
  monthDay?: number;
  // 원금 · 예산으로 살 수 있는 회차(없으면 제한 없음)
  affordable?: number;
}

interface ConditionalPlan {
  start: string;
  end: string;
  // 최대 횟수(없으면 제한 없음)
  max?: number;
  // 예산 ÷ 회당 금액(없으면 제한 없음)
  affordable?: number;
  // 익절 · 손절 · 트레일링 스탑 · 부분 익절 가운데 하나라도 켰는지
  sells: boolean;
}

// 원금 ÷ 회당 금액 내림(원금 · 회당 금액이 0 보다 큰 수가 아니면 제한 없음)
export const affordableRounds = (investment: string, amount: number): number | undefined => {
  const budget = parseFloat(investment);
  return budget > 0 && amount > 0 ? Math.floor(budget / amount) : undefined;
};

// 1년 회차 수로 상한 안에 드는 햇수
const maxYears = (roundsPerYear: number) =>
  Math.floor(Math.sqrt(MAX_ROUND_LOAD / (roundsPerYear * WEEKDAYS_PER_YEAR)));

const weekdays = (start: string, end: string) => countRounds('DAILY', 1, start, end);

// 적립식 회차(살 수 있는 회차까지) × 첫 매수일 ~ 끝 날짜 평일 수
export const dcaLoad = ({ cadence, months, first, end, monthDay, affordable = Infinity }: DcaPlan) =>
  Math.min(countRounds(cadence, months, first, end, monthDay), affordable) * weekdays(first, end);

// 적립식 회차가 상한을 넘으면 받은 주기로 최대 몇 년인지 안내
export const dcaRoundIssue = (plan: DcaPlan): string | null => {
  if (!(dcaLoad(plan) > MAX_ROUND_LOAD)) return null;
  const step = plan.cadence === 'MONTHS' ? plan.months : 1;
  const [every, perYear] =
    plan.cadence === 'DAILY'
      ? ['매일', WEEKDAYS_PER_YEAR]
      : plan.cadence === 'WEEKLY'
        ? ['매주', WEEKS_PER_YEAR]
        : [step > 1 ? `${step}개월마다` : '매달', 12 / step];
  return `${every} 적립은 최대 약 ${maxYears(perYear)}년 — 기간 줄이기 · 주기 늘리기`;
};

// 평일마다 살 수 있다고 본 조건부 회차(매도 규칙이 없으면 최대 횟수 · 예산으로 살 수 있는 회차까지) × 기간 평일 수
export const conditionalLoad = ({ start, end, max = Infinity, affordable = Infinity, sells }: ConditionalPlan) => {
  const days = weekdays(start, end);
  return (sells ? days : Math.min(days, max, affordable)) * days;
};

// 조건부 회차가 상한을 넘으면 매도 규칙이 있으면 최대 몇 년, 없으면 받은 기간의 최대 매수 횟수 안내
export const conditionalRoundIssue = (plan: ConditionalPlan): string | null => {
  if (!(conditionalLoad(plan) > MAX_ROUND_LOAD)) return null;
  if (plan.sells) return `매도 규칙이 있는 조건부 매수는 최대 약 ${maxYears(WEEKDAYS_PER_YEAR)}년 — 기간 줄이기`;
  const most = Math.floor(MAX_ROUND_LOAD / weekdays(plan.start, plan.end));
  return `이 기간이면 최대 매수 ${formatNumber(most)}회까지 — 최대 매수 횟수 정하기 · 기간 줄이기`;
};

// 전략 비교 칩 회차 합이 상한을 넘으면 안내
export const comparisonRoundIssue = (total: number): string | null =>
  total > MAX_COMPARISON_ROUND_LOAD ? '칩 회차 합이 너무 많음 — 칩 빼기 · 기간 줄이기 · 주기 늘리기' : null;
