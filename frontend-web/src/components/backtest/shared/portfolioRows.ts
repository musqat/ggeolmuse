import type { PortfolioAllocation, PortfolioMethod, PortfolioStrategyType } from '@services/api';
import { hasSellRates } from './sellRates';
import { MA_WINDOWS } from './maWindows';
import { MA_BAND } from './maBand';

// 줄 전략(빈 값은 방식대로)
export type RowStrategy = '' | PortfolioStrategyType;

// 포트폴리오 종목 줄 칸(종목 · 원화 금액 · 비율 % · 평균 단가 대비 익절 % · 손절 % · 전략과 이동평균 · 밴드 칸)과 판매 차례, 방식 · 전략을 바꿔도 칸 값은 남음
export interface PortfolioRow {
  symbol: string;
  amount: string;
  weight: string;
  // 비우면 안 함
  takeProfit: string;
  stopLoss: string;
  strategy: RowStrategy;
  // 이동평균 짧은 · 긴 기간(일)
  shortWindow: string;
  longWindow: string;
  // 밴드 평균 기간(일) · 위 · 아래 비율(%)
  bandWindow: string;
  upper: string;
  lower: string;
  // 양도세 낼 현금이 모자라 정한 순서로 팔 때의 차례(작을수록 먼저)
  taxSaleOrder: number;
}

export const MAX_ROWS = 10;

export const STRATEGIES: readonly PortfolioStrategyType[] = ['LUMP_SUM', 'DCA', 'MA_CROSS', 'MA_BAND'];

export const STRATEGY_LABELS: Record<PortfolioStrategyType, string> = {
  LUMP_SUM: '한 번에',
  DCA: '적립식',
  MA_CROSS: '이동평균 교차',
  MA_BAND: '밴드',
};

const SIGNAL_DEFAULTS = {
  shortWindow: MA_WINDOWS.short,
  longWindow: MA_WINDOWS.long,
  bandWindow: MA_BAND.window,
  upper: MA_BAND.upper,
  lower: MA_BAND.lower,
};

// 익절 · 손절이 비고 방식대로인 줄(이동평균 · 밴드 칸은 기본값)
export const newRow = (symbol = '', amount = '', weight = '', taxSaleOrder = 1): PortfolioRow => ({
  symbol,
  amount,
  weight,
  takeProfit: '',
  stopLoss: '',
  strategy: '',
  ...SIGNAL_DEFAULTS,
  taxSaleOrder,
});

// 먼저 팔 줄부터 줄 번호(0부터, 차례가 같으면 줄 차례)
export const saleOrder = (rows: PortfolioRow[]): number[] =>
  rows.map((_, index) => index).sort((a, b) => rows[a].taxSaleOrder - rows[b].taxSaleOrder || a - b);

// 먼저 팔 줄부터 줄 번호 목록을 줄마다 1부터의 차례로
const ranksOf = (order: number[]): number[] => {
  const ranks: number[] = [];
  order.forEach((index, position) => (ranks[index] = position + 1));
  return ranks;
};

// 줄마다 판매 차례(1 ~ 줄 수)
export const saleRanks = (rows: PortfolioRow[]): number[] => ranksOf(saleOrder(rows));

// 판매 차례를 1부터 다시 매긴 줄
const ranked = (rows: PortfolioRow[], order: number[]): PortfolioRow[] => {
  const ranks = ranksOf(order);
  return rows.map((row, index) => (row.taxSaleOrder === ranks[index] ? row : { ...row, taxSaleOrder: ranks[index] }));
};

// 판매 차례 position 의 줄을 위(-1) · 아래(+1) 차례와 바꿈(끝을 넘으면 차례만 다시 매김)
export const moveSaleOrder = (rows: PortfolioRow[], position: number, step: -1 | 1): PortfolioRow[] => {
  const order = saleOrder(rows);
  const other = position + step;
  if (position >= 0 && other >= 0 && position < order.length && other < order.length) {
    [order[position], order[other]] = [order[other], order[position]];
  }
  return ranked(rows, order);
};

// 줄 하나를 빼고 남은 줄 판매 차례를 앞뒤 그대로 1부터 다시 매김
export const removeRow = (rows: PortfolioRow[], index: number): PortfolioRow[] => {
  const left = rows.filter((_, i) => i !== index);
  return ranked(left, saleOrder(left));
};

// 방식대로인 줄은 방식
export const rowStrategy = (row: PortfolioRow, method: PortfolioMethod): PortfolioStrategyType => row.strategy || method;

// 방식과 같은 전략을 고르면 방식대로
export const chosenStrategy = (picked: PortfolioStrategyType, method: PortfolioMethod): RowStrategy =>
  picked === method ? '' : picked;

export const hasDcaRow = (rows: PortfolioRow[], method: PortfolioMethod) =>
  rows.some((row) => rowStrategy(row, method) === 'DCA');

export const isAllDca = (rows: PortfolioRow[], method: PortfolioMethod) =>
  rows.every((row) => rowStrategy(row, method) === 'DCA');

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

type SavedRow = Pick<PortfolioRow, 'symbol' | 'amount' | 'weight'> & Partial<Record<Exclude<keyof PortfolioRow, 'symbol' | 'amount' | 'weight'>, unknown>>;

const isRow = (value: unknown): value is SavedRow =>
  typeof value === 'object' &&
  value !== null &&
  typeof (value as SavedRow).symbol === 'string' &&
  typeof (value as SavedRow).amount === 'string' &&
  typeof (value as SavedRow).weight === 'string';

const textOr = (value: unknown, fallback = '') => (typeof value === 'string' ? value : fallback);

// 직전 설정에 저장한 줄(종목 · 금액 · 비율이 글 칸인 것만 10개까지, 익절 · 손절은 글이 아니면 빈 칸, 전략은 고를 수 있는 값만, 이동평균 · 밴드 칸은 글이 아니면 기본값, 판매 차례는 정수가 아니면 줄 차례)
export const savedRows = (saved: unknown[]): PortfolioRow[] =>
  saved
    .filter(isRow)
    .slice(0, MAX_ROWS)
    .map((row, index) => ({
      symbol: row.symbol,
      amount: row.amount,
      weight: row.weight,
      takeProfit: textOr(row.takeProfit),
      stopLoss: textOr(row.stopLoss),
      strategy: STRATEGIES.includes(row.strategy as PortfolioStrategyType) ? (row.strategy as PortfolioStrategyType) : '',
      shortWindow: textOr(row.shortWindow, SIGNAL_DEFAULTS.shortWindow),
      longWindow: textOr(row.longWindow, SIGNAL_DEFAULTS.longWindow),
      bandWindow: textOr(row.bandWindow, SIGNAL_DEFAULTS.bandWindow),
      upper: textOr(row.upper, SIGNAL_DEFAULTS.upper),
      lower: textOr(row.lower, SIGNAL_DEFAULTS.lower),
      taxSaleOrder: Number.isInteger(row.taxSaleOrder) ? (row.taxSaleOrder as number) : index + 1,
    }));

// 요약 첫 줄 종목(빈 줄은 빼고 셋까지, 나머지는 외 N)
export const rowsSubject = (rows: PortfolioRow[]): string => {
  const symbols = rows.map(rowSymbol).filter(Boolean);
  if (!symbols.length) return '종목';
  return `${symbols.slice(0, 3).join(', ')}${symbols.length > 3 ? ` 외 ${symbols.length - 3}` : ''}`;
};

// 요약 익절 · 손절 글(적은 줄 수와 적은 쪽, 하나도 없으면 빈 글)
export const rowsSellText = (rows: PortfolioRow[]): string => {
  const count = rows.filter((row) => hasSellRates(row.takeProfit, row.stopLoss)).length;
  if (!count) return '';
  const kinds = [rows.some((row) => row.takeProfit.trim()) && '익절', rows.some((row) => row.stopLoss.trim()) && '손절'];
  return `${count}개 종목에 ${kinds.filter(Boolean).join(' · ')}(평균 단가 기준 전량)`;
};
