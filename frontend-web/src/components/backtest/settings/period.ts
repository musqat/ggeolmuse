import { useState } from 'react';
import { getTodayString, parseLocalDate, toLocalDateString } from '../../../utils/dateUtils';
import { toWeekday } from '../shared/dcaSchedule';
import type { FieldMessage } from './checks';
import { oneOf, pickValues } from './snapshot';

// 오늘(끝 날짜)부터 몇 주 · 달 · 년 전
export type AgoUnit = 'WEEK' | 'MONTH' | 'YEAR';

export const AGO_UNITS: { value: AgoUnit; label: string }[] = [
  { value: 'WEEK', label: '주' },
  { value: 'MONTH', label: '달' },
  { value: 'YEAR', label: '년' },
];

export const agoWord = (unit: AgoUnit) => AGO_UNITS.find((option) => option.value === unit)!.label;

// 1 ~ 99 정수만 몇 주 · 달 · 년으로
export const parseAgo = (count: string): number | undefined => {
  const value = Number(count);
  return count.trim() !== '' && Number.isInteger(value) && value >= 1 && value <= 99 ? value : undefined;
};

// 끝 날짜에서 N주 · N달 · N년 전(그 달에 같은 날짜가 없으면 그 달 말일)
export const dateBefore = (end: string, count: number, unit: AgoUnit): string => {
  const date = parseLocalDate(end);
  if (unit === 'WEEK') {
    date.setDate(date.getDate() - 7 * count);
    return toLocalDateString(date);
  }
  const month = date.getMonth() - (unit === 'YEAR' ? 12 * count : count);
  const lastDay = new Date(date.getFullYear(), month + 1, 0).getDate();
  return toLocalDateString(new Date(date.getFullYear(), month, Math.min(date.getDate(), lastDay)));
};

// 빠른 고르기로 센 시작일(주말이면 다음 월요일)
export const presetStart = (end: string, count: number, unit: AgoUnit) => toWeekday(dateBefore(end, count, unit));

// 빠른 고르기 앞말(끝 날짜가 오늘이 아니면 그 날짜부터)
export const fromLabel = (end: string, today: string) => {
  if (end === today) return '오늘부터';
  const date = parseLocalDate(end);
  return `${date.getMonth() + 1}월 ${date.getDate()}일부터`;
};

// 2025-10-08 → 2025.10.08
export const dotDate = (date: string) => date.replace(/-/g, '.');

interface PeriodCheck {
  start: string;
  end: string;
  agoOn: boolean;
  agoCount: string;
}

// 기간 칸 오류(끝 날짜는 빈 칸을 오늘로 채운 값)
export const periodIssue = ({ start, end, agoOn, agoCount }: PeriodCheck): FieldMessage | null => {
  if (agoOn && parseAgo(agoCount) === undefined) {
    return { kind: 'error', text: Number(agoCount) > 99 ? '99 이하' : '몇 주 · 달 · 년 전인지 1 이상' };
  }
  if (end <= start) return { kind: 'error', text: '끝 날짜가 시작일과 같거나 앞' };
  return null;
};

export interface PeriodValues {
  start: string;
  // 비면 오늘
  end: string;
  agoCount: string;
  agoUnit: AgoUnit;
  // 빠른 고르기가 시작일을 정하는지
  agoOn: boolean;
}

export interface PeriodState extends PeriodValues {
  setStart: (date: string) => void;
  setEnd: (date: string) => void;
  setAgoCount: (count: string) => void;
  setAgoUnit: (unit: AgoUnit) => void;
  // 저장할 값(끝 날짜가 오늘이면 비움)
  values: PeriodValues;
  // 저장한 값(빠른 고르기가 켜져 있으면 시작일을 다시 셈, 모양이 다른 칸은 기본값)
  load: (saved: unknown) => void;
}

const DEFAULT_PERIOD: PeriodValues = { start: '', end: '', agoCount: '1', agoUnit: 'YEAR', agoOn: true };

// 모드가 같이 쓰는 기간: 처음은 오늘부터 1년 전, 숫자 · 단위를 고치면 켜지고 시작일을 직접 고르면 꺼짐
export const usePeriod = (): PeriodState => {
  const [end, setEndValue] = useState(DEFAULT_PERIOD.end);
  const [agoCount, setAgoCountValue] = useState(DEFAULT_PERIOD.agoCount);
  const [agoUnit, setAgoUnitValue] = useState<AgoUnit>(DEFAULT_PERIOD.agoUnit);
  const [agoOn, setAgoOn] = useState(DEFAULT_PERIOD.agoOn);
  const [start, setStartValue] = useState(() => presetStart(getTodayString(), 1, 'YEAR'));

  // 숫자가 범위 밖이면 시작일을 그대로 둠
  const follow = (count: string, unit: AgoUnit, endDate: string) => {
    const value = parseAgo(count);
    if (value !== undefined) setStartValue(presetStart(endDate || getTodayString(), value, unit));
  };

  const load = (saved: unknown) => {
    const next = pickValues(DEFAULT_PERIOD, saved);
    const unit = oneOf(next.agoUnit, AGO_UNITS.map((option) => option.value), DEFAULT_PERIOD.agoUnit);
    const count = parseAgo(next.agoCount);
    setEndValue(next.end);
    setAgoCountValue(next.agoCount);
    setAgoUnitValue(unit);
    setAgoOn(next.agoOn);
    if (next.agoOn && count !== undefined) setStartValue(presetStart(next.end || getTodayString(), count, unit));
    else setStartValue(next.start || presetStart(getTodayString(), 1, 'YEAR'));
  };

  return {
    start,
    end,
    agoCount,
    agoUnit,
    agoOn,
    values: { start, end: end === getTodayString() ? '' : end, agoCount, agoUnit, agoOn },
    load,
    setStart: (date) => {
      setStartValue(date);
      setAgoOn(false);
    },
    setEnd: (date) => {
      setEndValue(date);
      if (agoOn) follow(agoCount, agoUnit, date);
    },
    setAgoCount: (count) => {
      const digits = count.replace(/[^0-9]/g, '');
      setAgoCountValue(digits);
      setAgoOn(true);
      follow(digits, agoUnit, end);
    },
    setAgoUnit: (unit) => {
      setAgoUnitValue(unit);
      setAgoOn(true);
      follow(agoCount, unit, end);
    },
  };
};
