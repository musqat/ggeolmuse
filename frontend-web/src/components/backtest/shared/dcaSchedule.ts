import type { DcaFrequency } from '@services/api';
import { parseLocalDate, toLocalDateString } from '../../../utils/dateUtils';

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

// 첫 매수일로 정한 매수 날짜 안내(매주는 주말이면 월요일, 29일 이후는 그 날짜가 없는 달 규칙까지)
export const scheduleText = (cadence: DcaCadence, months: string, firstDate: string): string => {
  if (cadence === 'DAILY') return '거래일마다';
  if (cadence === 'WEEKLY') return `매주 ${WEEKDAYS[parseLocalDate(toWeekday(firstDate)).getDay()]}요일`;
  const day = dayOfMonth(firstDate);
  const every = cadence === 'MONTHLY' ? '매달' : `${parseMonths(months) ?? '?'}개월마다`;
  return day > 28 ? `${every} ${day}일 · ${day}일이 없는 달은 그 달 마지막 거래일` : `${every} ${day}일`;
};
