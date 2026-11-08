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
