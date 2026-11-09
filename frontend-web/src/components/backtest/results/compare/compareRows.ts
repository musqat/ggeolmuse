import type { ComparisonItem, PerformanceMetrics } from '../../../../services/api';
import { hasValue } from '../../../../utils/gainLoss';
import { assetSeries, type Deposit } from '../assetSeries';
import type { MetricCurrency } from '../CurrencyToggle';

// 비교 표 한 줄
export interface CompareRow {
  key: string;
  name: string;
  color: string;
  item: ComparisonItem;
  totalReturnPercent: number | null;
  valueKrw: number | null;
  metricsKrw?: PerformanceMetrics | null;
  metricsUsd?: PerformanceMetrics | null;
  deposits: Deposit[];
}

// 날짜별 수익률(%)
export interface ReturnPoint {
  date: string;
  pct: number;
}

export const metricsOf = (row: CompareRow, currency: MetricCurrency) =>
  currency === 'KRW' ? row.metricsKrw : row.metricsUsd;

// 예상 양도세(요청하지 않았으면 null)
export const taxOf = (row: CompareRow) => row.item.capitalGainsTax ?? row.item.additionalData?.capitalGainsTax ?? null;

// 값이 가장 큰 줄의 key(값이 있는 줄이 둘 이상일 때만, 같으면 모두)
export function bestKeys(rows: CompareRow[], value: (row: CompareRow) => number | null | undefined): Set<string> {
  const valued = rows.filter((row) => hasValue(value(row)));
  if (valued.length < 2) return new Set();
  const top = Math.max(...valued.map((row) => value(row) as number));
  return new Set(valued.filter((row) => value(row) === top).map((row) => row.key));
}

// 일별 값 ÷ 그때까지 넣은 돈 - 1
export const returnPoints = (row: CompareRow, currency: MetricCurrency): ReturnPoint[] =>
  assetSeries(row.item.additionalData?.dailyValues ?? [], row.deposits, currency).flatMap((point) =>
    point.returnPct == null ? [] : [{ date: point.date, pct: point.returnPct }],
  );

// 총 수익률 큰 순
export const byTotalReturn = (a: CompareRow, b: CompareRow) =>
  (b.totalReturnPercent ?? -Infinity) - (a.totalReturnPercent ?? -Infinity);

const MULTIPLE_TICKS = [0.125, 0.25, 0.5, 1, 2, 4, 8, 16, 32, 64, 128];

// 배수 눈금 글자(2 → +100%)
export const multipleLabel = (multiple: number) => {
  const pct = Math.round((multiple - 1) * 100);
  return `${pct > 0 ? '+' : ''}${pct}%`;
};

const LINEAR_AXIS = { scale: 'linear' as const, domain: ['auto', 'auto'] as const, ticks: undefined };

// 배수 축: 2배 넘게 벌어지고 배수 눈금이 둘 이상이면 로그 눈금(0.5 · 1 · 2 · 4배 …), 아니면 보통 간격
export const multipleAxis = (values: number[]) => {
  const lo = Math.min(...values);
  const hi = Math.max(...values);
  if (!(lo > 0) || hi / lo < 2) return LINEAR_AXIS;
  const domain: [number, number] = [lo * 0.95, hi * 1.05];
  const ticks = MULTIPLE_TICKS.filter((t) => t >= domain[0] && t <= domain[1]);
  return ticks.length < 2 ? LINEAR_AXIS : { scale: 'log' as const, domain, ticks };
};
