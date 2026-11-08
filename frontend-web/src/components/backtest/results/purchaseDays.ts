import type { PurchaseDayReturn } from '../../../services/api';
import type { MetricCurrency } from './CurrencyToggle';
import { zeroOffset } from './resultValues';

// 매수일과 그날 샀다면 평가일까지 수익률(%)
export type PurchaseDayPoint = { date: string; returnPct: number };

// 고른 통화의 매수일별 수익률(값이 없는 날은 뺌)
export const purchaseDaySeries = (
  returns: PurchaseDayReturn[] | undefined,
  currency: MetricCurrency,
): PurchaseDayPoint[] =>
  (returns ?? []).flatMap(({ date, returnPercent, returnPercentUsd }) => {
    const value = currency === 'KRW' ? returnPercent : returnPercentUsd;
    return value == null ? [] : [{ date, returnPct: value }];
  });

// 목표 이상인 날 수 · 비율(%)과 가장 좋은 매수일(같은 값이면 앞선 날)
export const targetSummary = (points: PurchaseDayPoint[], target: number) => {
  const hits = points.filter((p) => p.returnPct >= target).length;
  const best = points.reduce<PurchaseDayPoint | null>(
    (top, p) => (top == null || p.returnPct > top.returnPct ? p : top),
    null,
  );
  return { total: points.length, hits, share: points.length ? (hits / points.length) * 100 : 0, best };
};

// 목표선 위 · 아래로 색이 갈리는 지점(위쪽 비율)
export const targetOffset = (min: number, max: number, target: number) => zeroOffset(min - target, max - target);

// 비교에 넣을 수 있는 매수일 수
export const MAX_COMPARE = 5;

// 비교에 넣기(고른 매수일 · 이미 있는 날은 그대로, 넘치면 먼저 넣은 날부터 뺌)
export const addCompare = (dates: string[], date: string, purchaseDate?: string | null) => {
  if (date === purchaseDate || dates.includes(date)) return dates;
  return [...dates, date].slice(-MAX_COMPARE);
};

export const removeCompare = (dates: string[], date: string) => dates.filter((d) => d !== date);

// 그날이나 그 뒤 첫 매수일(마지막 매수일보다 뒤면 null)
export const tradingDayOnOrAfter = (points: PurchaseDayPoint[], date: string) =>
  points.find((p) => p.date >= date)?.date ?? null;

// 비교 표 한 줄(값이 없는 날은 null, 차이는 고른 매수일 대비 %p)
export type CompareRow = { date: string; returnPct: number | null; diff: number | null; base: boolean };

// 고른 매수일을 맨 위에, 넣은 날은 날짜순
export const compareRows = (
  points: PurchaseDayPoint[],
  dates: string[],
  purchaseDate?: string | null,
): CompareRow[] => {
  const valueOn = (date: string) => points.find((p) => p.date === date)?.returnPct ?? null;
  const baseValue = purchaseDate ? valueOn(purchaseDate) : null;
  const rows = [...dates].sort().map((date) => {
    const returnPct = valueOn(date);
    const diff = returnPct != null && baseValue != null ? returnPct - baseValue : null;
    return { date, returnPct, diff, base: false };
  });
  return purchaseDate ? [{ date: purchaseDate, returnPct: baseValue, diff: null, base: true }, ...rows] : rows;
};
