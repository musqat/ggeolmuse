import { subtractMonths } from '../../../utils/dateUtils';

// 주소 쿼리 period 값과 버튼 이름. months 만큼 오늘에서 거슬러 간다
export const PERIODS = [
  { code: '1m', label: '1개월', months: 1 },
  { code: '3m', label: '3개월', months: 3 },
  { code: '6m', label: '6개월', months: 6 },
  { code: '1y', label: '1년', months: 12 },
  { code: '3y', label: '3년', months: 36 },
  { code: '5y', label: '5년', months: 60 },
  { code: '10y', label: '10년', months: 120 },
  { code: 'all', label: '전체', months: null },
] as const;

export type PeriodCode = (typeof PERIODS)[number]['code'] | 'custom';

export const DEFAULT_PERIOD: PeriodCode = '1y';

// 전체는 DB 에 있는 가장 오래된 데이터부터 받는다
const ALL_START = '1970-01-01';

export type ChartRange =
  | { status: 'ready'; startDate: string; endDate: string }
  // 직접설정에서 날짜를 다 고르지 않았다
  | { status: 'incomplete' }
  | { status: 'invalid'; message: string };

// 모르는 값이면 기본 기간
export function parsePeriod(value: string | null): PeriodCode {
  if (value === 'custom') return 'custom';
  return PERIODS.find((p) => p.code === value)?.code ?? DEFAULT_PERIOD;
}

export function rangeForPeriod(period: PeriodCode, today: string, start = '', end = ''): ChartRange {
  if (period === 'custom') {
    if (!start || !end) return { status: 'incomplete' };
    if (start >= end) return { status: 'invalid', message: '시작일은 종료일보다 이전이어야 합니다.' };
    return { status: 'ready', startDate: start, endDate: end };
  }

  const { months } = PERIODS.find((p) => p.code === period)!;
  return {
    status: 'ready',
    startDate: months === null ? ALL_START : subtractMonths(today, months),
    endDate: today,
  };
}
