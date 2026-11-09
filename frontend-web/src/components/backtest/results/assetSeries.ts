import type { DailyValue, StrategyTransaction } from '../../../services/api';
import type { MetricCurrency } from './CurrencyToggle';

// 그 날짜에 넣은 돈(달러는 매수 환율로 바꾼 값, 모르면 null)
export interface Deposit {
  date: string;
  krw: number;
  usd: number | null;
}

// 그 날짜의 평가 가치 · 그 날짜까지 넣은 돈 · 수익률(%), 넣은 돈이 없으면 null
export interface AssetPoint {
  date: string;
  value: number;
  invested: number | null;
  returnPct: number | null;
}

// 고른 통화의 일별 평가 가치와 그 날짜까지 넣은 돈 · 수익률
export function assetSeries(values: DailyValue[], deposits: Deposit[], currency: MetricCurrency): AssetPoint[] {
  const sorted = [...deposits].sort((a, b) => a.date.localeCompare(b.date));
  let next = 0;
  let invested = 0;
  let usdKnown = true;
  return values.map((day) => {
    while (next < sorted.length && sorted[next].date <= day.date) {
      const deposit = sorted[next++];
      if (currency === 'KRW') invested += deposit.krw;
      else if (deposit.usd == null) usdKnown = false;
      else invested += deposit.usd;
    }
    const value = currency === 'KRW' ? day.valueKrw : day.valueUsd;
    const known = invested > 0 && (currency === 'KRW' || usdKnown) ? invested : null;
    return { date: day.date, value, invested: known, returnPct: known ? (value / known - 1) * 100 : null };
  });
}

// 매수한 날의 점(그 날짜의 일별 값이 없으면 다음 날, 같은 날은 하나)
export function depositPoints(points: AssetPoint[], deposits: Deposit[]): AssetPoint[] {
  const picked = new Set<AssetPoint>();
  for (const deposit of deposits) {
    const point = points.find((p) => p.date >= deposit.date);
    if (point) picked.add(point);
  }
  return points.filter((p) => picked.has(p));
}

// 단순 매수의 넣은 돈
export const simpleDeposits = (
  date: string | null | undefined,
  krw: number | null | undefined,
  fxRate: number | null | undefined,
): Deposit[] => (date && krw ? [{ date, krw, usd: fxRate && fxRate > 0 ? krw / fxRate : null }] : []);

// 적립식 · 조건부의 넣은 돈(금액 0 인 배당 재투자 제외)
export const strategyDeposits = (transactions: StrategyTransaction[]): Deposit[] =>
  transactions
    .filter((tx) => tx.amount > 0)
    .map((tx) => ({
      date: tx.actualDate ?? tx.date,
      krw: tx.amount,
      usd: tx.fxRate > 0 ? tx.amount / tx.fxRate : null,
    }));
