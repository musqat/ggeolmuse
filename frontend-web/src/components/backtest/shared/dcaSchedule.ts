import type { DcaFrequency, DcaStrategyRequest } from '@services/api';
import { daysBetween, getTodayString, parseLocalDate, toLocalDateString } from '../../../utils/dateUtils';

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

// 첫 매수일로 정한 주기 · 요일 · 며칠(매주는 주말이면 월요일 · 월요일은 그 주 첫 거래일, monthDay 를 주면 그 며칠)
export const cadenceText = (cadence: DcaCadence, months: string, firstDate: string, monthDay?: number): string => {
  if (cadence === 'DAILY') return '거래일마다';
  if (cadence === 'WEEKLY') {
    const weekday = parseLocalDate(toWeekday(firstDate)).getDay();
    return weekday === 1 ? '매주 첫 거래일' : `매주 ${WEEKDAYS[weekday]}요일`;
  }
  const every = cadence === 'MONTHLY' ? '매달' : `${parseMonths(months) ?? '?'}개월마다`;
  return `${every} ${monthDay ?? dayOfMonth(firstDate)}일`;
};

// 고른 첫 매수일과 고를 때의 시작일(date 가 비면 시작일을 따라감)
export interface FirstPick {
  date: string;
  start: string;
}

// 그 달의 며칠(없으면 그 달 말일)
const dayIn = (year: number, month: number, day: number) =>
  new Date(year, month, Math.min(day, new Date(year, month + 1, 0).getDate()));

// 지금 시작일의 첫 매수일: 고를 때와 시작일이 같으면 고른 날(매주는 주말이면 월요일), 바뀌면 같은 요일 · 며칠의 시작일 뒤 첫 날(고르지 않았으면 빈 값)
export const resolveFirstPurchase = (startDate: string, cadence: DcaCadence, pick: FirstPick): string => {
  if (!pick.date) return '';
  if ((pick.start || startDate) === startDate && pick.date >= startDate) {
    return cadence === 'WEEKLY' ? toWeekday(pick.date) : pick.date;
  }
  if (cadence === 'DAILY') return toWeekday(startDate);
  const start = parseLocalDate(startDate);
  if (cadence === 'WEEKLY') {
    const weekday = parseLocalDate(toWeekday(pick.date)).getDay();
    while (start.getDay() !== weekday) start.setDate(start.getDate() + 1);
    return toLocalDateString(start);
  }
  const day = dayOfMonth(pick.date);
  const first = dayIn(start.getFullYear(), start.getMonth(), day);
  return toLocalDateString(first < start ? dayIn(start.getFullYear(), start.getMonth() + 1, day) : first);
};

interface ScheduleInput {
  cadence: DcaCadence;
  // N개월마다의 개월 수
  months: number;
  firstPurchaseDate: string;
  // 고른 첫 매수일의 며칠(그 달에 없어 말일로 옮긴 첫 매수일의 며칠 대신 씀)
  monthDay?: number;
  startDate: string;
  endDate: string;
}

// 주기 요청 칸: 매달은 첫 매수일(비우면 시작일)의 며칠, 매주는 주말 첫 매수일을 다음 월요일로(종료일을 넘으면 그대로)
export const scheduleFields = ({ cadence, months, firstPurchaseDate, monthDay, startDate, endDate }: ScheduleInput) => {
  const base = firstPurchaseDate || startDate;
  const weekday = toWeekday(base);
  const first = cadence === 'WEEKLY' && weekday <= endDate ? weekday : firstPurchaseDate;
  const fields: Pick<DcaStrategyRequest, 'frequency' | 'firstPurchaseDate' | 'purchaseDay' | 'investmentInterval'> = {
    frequency: toFrequency(cadence),
  };
  if (first) fields.firstPurchaseDate = first;
  if (isMonthly(cadence)) {
    fields.purchaseDay = monthDay ?? dayOfMonth(base);
    fields.investmentInterval = cadence === 'MONTHS' ? months : 1;
  }
  return fields;
};

const yesterday = () => {
  const day = parseLocalDate(getTodayString());
  day.setDate(day.getDate() - 1);
  return toLocalDateString(day);
};

// 첫 매수일부터 종료일까지 회차 수(종가가 없는 오늘 회차는 뺌, 휴장일은 모르고 주말만 뺌, 매달은 없는 날을 그 달 말일로)
export const countRounds = (
  cadence: DcaCadence,
  months: number,
  firstDate: string,
  lastDate: string,
  monthDay?: number,
): number => {
  const closed = yesterday();
  const endDate = lastDate > closed ? closed : lastDate;
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
  const day = monthDay ?? first.getDate();
  const planned = (round: number) => dayIn(first.getFullYear(), first.getMonth() + round * step, day);
  let count = 0;
  while (planned(count) <= end) count++;
  // 종료일이 말일 전인 달의 회차는 투자일 ~ 종료일에 평일이 있을 때만
  const last = planned(count - 1);
  const endMonth = last.getFullYear() === end.getFullYear() && last.getMonth() === end.getMonth();
  const monthEnd = end.getDate() === new Date(end.getFullYear(), end.getMonth() + 1, 0).getDate();
  if (count > 0 && endMonth && !monthEnd && toWeekday(toLocalDateString(last)) > endDate) count--;
  return count;
};
