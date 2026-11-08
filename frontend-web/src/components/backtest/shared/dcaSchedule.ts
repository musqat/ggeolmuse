import type { DcaFrequency, DcaStrategyRequest } from '@services/api';
import { daysBetween, parseLocalDate, toLocalDateString } from '../../../utils/dateUtils';

// 화면의 투자 주기(N개월마다는 요청에서 매달 + 개월 수)
export type DcaCadence = DcaFrequency | 'MONTHS';

export const CADENCE_OPTIONS: { value: DcaCadence; label: string }[] = [
  { value: 'DAILY', label: '매일' },
  { value: 'WEEKLY', label: '매주' },
  { value: 'MONTHLY', label: '매달' },
  { value: 'MONTHS', label: 'N개월마다' },
];

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

export const toFrequency = (cadence: DcaCadence): DcaFrequency => (cadence === 'MONTHS' ? 'MONTHLY' : cadence);

// 저장된 문자열의 주기(모르는 값 · 빈 값은 매달)
export const toCadence = (value?: string): DcaCadence =>
  CADENCE_OPTIONS.find((option) => option.value === value)?.value ?? 'MONTHLY';

export const isMonthly = (cadence: DcaCadence) => cadence === 'MONTHLY' || cadence === 'MONTHS';

// 1 ~ 12 정수만 개월 수로
export const parseMonths = (months: string): number | undefined => {
  const value = Number(months);
  return months.trim() !== '' && Number.isInteger(value) && value >= 1 && value <= 12 ? value : undefined;
};

export const dayOfMonth = (date: string) => parseLocalDate(date).getDate();

// 주말이면 다음 월요일
export const toWeekday = (date: string) => {
  const day = parseLocalDate(date);
  const weekday = day.getDay();
  if (weekday === 6) day.setDate(day.getDate() + 2);
  if (weekday === 0) day.setDate(day.getDate() + 1);
  return toLocalDateString(day);
};

// 첫 매수일로 정한 주기 · 요일 · 며칠(매주는 주말이면 월요일)
export const cadenceText = (cadence: DcaCadence, months: string, firstDate: string): string => {
  if (cadence === 'DAILY') return '거래일마다';
  if (cadence === 'WEEKLY') return `매주 ${WEEKDAYS[parseLocalDate(toWeekday(firstDate)).getDay()]}요일`;
  const every = cadence === 'MONTHLY' ? '매달' : `${parseMonths(months) ?? '?'}개월마다`;
  return `${every} ${dayOfMonth(firstDate)}일`;
};

// 첫 매수일로 정한 매수 날짜 안내(29일 이후는 그 날짜가 없는 달 규칙까지)
export const scheduleText = (cadence: DcaCadence, months: string, firstDate: string): string => {
  const text = cadenceText(cadence, months, firstDate);
  const day = dayOfMonth(firstDate);
  return isMonthly(cadence) && day > 28 ? `${text} · ${day}일이 없는 달은 그 달 마지막 거래일` : text;
};

interface ScheduleInput {
  cadence: DcaCadence;
  // N개월마다의 개월 수
  months: number;
  firstPurchaseDate: string;
  startDate: string;
  endDate: string;
}

// 주기 요청 칸: 매달은 첫 매수일(비우면 시작일)의 며칠, 매주는 주말 첫 매수일을 다음 월요일로(종료일을 넘으면 그대로)
export const scheduleFields = ({ cadence, months, firstPurchaseDate, startDate, endDate }: ScheduleInput) => {
  const base = firstPurchaseDate || startDate;
  const weekday = toWeekday(base);
  const first = cadence === 'WEEKLY' && weekday <= endDate ? weekday : firstPurchaseDate;
  const fields: Pick<DcaStrategyRequest, 'frequency' | 'firstPurchaseDate' | 'purchaseDay' | 'investmentInterval'> = {
    frequency: toFrequency(cadence),
  };
  if (first) fields.firstPurchaseDate = first;
  if (isMonthly(cadence)) {
    fields.purchaseDay = dayOfMonth(base);
    fields.investmentInterval = cadence === 'MONTHS' ? months : 1;
  }
  return fields;
};

// 그 달의 며칠(없으면 그 달 말일)
const dayIn = (year: number, month: number, day: number) =>
  new Date(year, month, Math.min(day, new Date(year, month + 1, 0).getDate()));

// 첫 매수일부터 종료일까지 회차 수(휴장일은 모르고 주말만 뺌, 매달은 없는 날을 그 달 말일로)
export const countRounds = (cadence: DcaCadence, months: number, firstDate: string, endDate: string): number => {
  if (cadence === 'DAILY') {
    let count = 0;
    for (const day = parseLocalDate(firstDate); toLocalDateString(day) <= endDate; day.setDate(day.getDate() + 1)) {
      if (day.getDay() % 6 !== 0) count++;
    }
    return count;
  }
  if (cadence === 'WEEKLY') {
    const first = toWeekday(firstDate);
    return first > endDate ? 0 : Math.floor(daysBetween(first, endDate) / 7) + 1;
  }
  const step = cadence === 'MONTHS' ? months : 1;
  if (!(Number.isInteger(step) && step >= 1)) return 0;
  const first = parseLocalDate(firstDate);
  const end = parseLocalDate(endDate);
  let count = 0;
  while (dayIn(first.getFullYear(), first.getMonth() + count * step, first.getDate()) <= end) count++;
  return count;
};
