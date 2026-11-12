import type { PortfolioAllocation } from '@services/api';

// 포트폴리오 종목 줄 칸(종목 · 원화 금액 · 비율 %, 금액 · 비율 방식을 바꿔도 두 칸 다 남음)
export interface PortfolioRow {
  symbol: string;
  amount: string;
  weight: string;
}

export const MAX_ROWS = 10;

// 첫 줄 금액 · 비율 방식 총액의 기본값
export const FIRST_AMOUNT = '10000000';

// 비율을 더하는 단위(0.000001%)
const WEIGHT_UNIT = 1_000_000;

// 비율 합 100 에서 받는 차(0.01%p, 서버와 같은 값)
const WEIGHT_TOLERANCE = 10_000;

// 빈 칸 · 못 읽는 글은 NaN
const numberOf = (text: string) => (text.trim() ? Number(text.trim()) : NaN);

const orZero = (value: number) => (Number.isFinite(value) && value > 0 ? value : 0);

// 앞뒤 공백을 뺀 대문자 종목
export const rowSymbol = (row: PortfolioRow) => row.symbol.trim().toUpperCase();

// 칸 값(비율 칸 · 금액 칸, 빈 칸 · 못 읽는 글은 NaN)
export const rowWeight = (row: PortfolioRow) => numberOf(row.weight);
export const rowAmount = (row: PortfolioRow) => numberOf(row.amount);

// 줄마다 원화 금액(비율 방식은 총액 × 비율 ÷ 100 원 단위 반올림, 못 읽는 칸은 0)
export const rowAmounts = (rows: PortfolioRow[], allocation: PortfolioAllocation, total: string): number[] => {
  if (allocation === 'AMOUNT') return rows.map((row) => orZero(rowAmount(row)));
  const totalAmount = orZero(numberOf(total));
  return rows.map((row) => Math.round((totalAmount * orZero(rowWeight(row))) / 100));
};

// 금액 방식 줄마다 합계 대비 몫(%, 합이 0 이면 0)
export const amountShares = (rows: PortfolioRow[]): number[] => {
  const amounts = rowAmounts(rows, 'AMOUNT', '');
  const sum = amounts.reduce((a, b) => a + b, 0);
  return amounts.map((amount) => (sum > 0 ? (amount / sum) * 100 : 0));
};

// 비율 합(0.000001% 단위 정수)
const weightUnits = (rows: PortfolioRow[]) =>
  rows.reduce((sum, row) => sum + Math.round(orZero(rowWeight(row)) * WEIGHT_UNIT), 0);

// 비율 합(%)
export const weightSum = (rows: PortfolioRow[]): number => weightUnits(rows) / WEIGHT_UNIT;

export const isWeightSumOk = (rows: PortfolioRow[]): boolean =>
  Math.abs(weightUnits(rows) - 100 * WEIGHT_UNIT) <= WEIGHT_TOLERANCE;

const isRow = (value: unknown): value is PortfolioRow =>
  typeof value === 'object' &&
  value !== null &&
  typeof (value as PortfolioRow).symbol === 'string' &&
  typeof (value as PortfolioRow).amount === 'string' &&
  typeof (value as PortfolioRow).weight === 'string';

// 직전 설정에 저장한 줄(글 칸 셋인 것만 10개까지)
export const savedRows = (saved: unknown[]): PortfolioRow[] =>
  saved
    .filter(isRow)
    .slice(0, MAX_ROWS)
    .map(({ symbol, amount, weight }) => ({ symbol, amount, weight }));

// 요약 첫 줄 종목(빈 줄은 빼고 셋까지, 나머지는 외 N)
export const rowsSubject = (rows: PortfolioRow[]): string => {
  const symbols = rows.map(rowSymbol).filter(Boolean);
  if (!symbols.length) return '종목';
  return `${symbols.slice(0, 3).join(', ')}${symbols.length > 3 ? ` 외 ${symbols.length - 3}` : ''}`;
};
