import { formatReadableWon } from '../../../utils/format';
import { parseShares } from '../shared/amountUnit';
import { isMonthly, parseMonths, type DcaCadence } from '../shared/dcaSchedule';

// 칸 아래 한 줄(오류는 실행을 막고 경고는 안 막음)
export interface FieldMessage {
  kind: 'error' | 'warning';
  text: string;
}

const error = (text: string): FieldMessage => ({ kind: 'error', text });
const warning = (text: string): FieldMessage => ({ kind: 'warning', text });

export const isError = (message?: FieldMessage | null) => message?.kind === 'error';

export const countErrors = (messages: (FieldMessage | null | undefined)[]) => messages.filter(isError).length;

// 목록을 아직 못 받았으면 목록은 보지 않음
export const symbolIssue = (symbol: string, supportedSymbols: string[]): FieldMessage | null => {
  if (!symbol.trim()) return error('종목 비어 있음');
  if (supportedSymbols.length > 0 && !supportedSymbols.includes(symbol.toUpperCase())) return error('목록에서 고르기');
  return null;
};

export const symbolsIssue = (symbols: string[]): FieldMessage | null => (symbols.length < 2 ? error('종목을 둘 이상') : null);

interface AmountCheck {
  byShares: boolean;
  amount: string;
  shares: string;
  // 칸 이름(비어 있음 앞말)
  label: string;
}

export const amountIssue = ({ byShares, amount, shares, label }: AmountCheck): FieldMessage | null => {
  if (byShares) return parseShares(shares) === undefined ? error('1 이상 정수') : null;
  if (!amount.trim()) return error(`${label} 비어 있음`);
  return parseFloat(amount) > 0 ? null : error('0 보다 크게');
};

interface BudgetCheck {
  budget: string;
  // 한 회차에 쓸 돈(주식 수는 추정, 모르면 undefined)
  perRound: number | undefined;
  byShares: boolean;
}

// 비면 제한 없음, 한 번 살 돈보다 적으면 금액은 오류 · 주식 수는 추정이라 경고
export const budgetIssue = ({ budget, perRound, byShares }: BudgetCheck): FieldMessage | null => {
  if (!budget.trim()) return null;
  const value = parseFloat(budget);
  if (!(value > 0)) return error('0 보다 크게');
  if (perRound && value < perRound) {
    return byShares ? warning(`한 번 살 돈(≈ ${formatReadableWon(perRound)})보다 적음`) : error('한 번 살 돈보다 적음');
  }
  return null;
};

export const monthsIssue = (cadence: DcaCadence, months: string): FieldMessage | null =>
  cadence === 'MONTHS' && parseMonths(months) === undefined ? error('개월은 1 ~ 12') : null;

interface FirstCheck {
  first: string;
  // 빈 칸을 오늘로 채운 끝 날짜
  end: string;
  cadence: DcaCadence;
  monthDay: number;
}

// 끝 날짜 뒤면 오류, 매달 29일 이후면 없는 달 규칙 안내
export const firstIssue = ({ first, end, cadence, monthDay }: FirstCheck): FieldMessage | null => {
  if (first > end) return error('첫 매수일이 끝 날짜 뒤');
  if (isMonthly(cadence) && monthDay > 28) return warning(`${monthDay}일이 없는 달은 그 달 마지막 거래일`);
  return null;
};

export const percentIssue = (percent: string): FieldMessage | null => {
  if (!percent.trim()) return error('% 비어 있음');
  const value = parseFloat(percent);
  return value > 0 && value <= 100 ? null : error('0 ~ 100 사이');
};

export const sellIssue = (takeProfit: string, stopLoss: string): FieldMessage | null => {
  if ([takeProfit, stopLoss].some((value) => value.trim() && !(parseFloat(value) > 0))) return error('0 보다 크게');
  if (stopLoss.trim() && parseFloat(stopLoss) >= 100) return error('손절은 100 아래');
  return null;
};

export const maxIssue = (max: string): FieldMessage | null => {
  if (!max.trim()) return null;
  const value = Number(max);
  return Number.isInteger(value) && value >= 1 ? null : error('1 이상 정수');
};
