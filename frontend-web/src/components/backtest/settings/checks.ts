import { formatNumber, formatReadableWon } from '../../../utils/format';
import { MAX_SHARES, parseShares } from '../shared/amountUnit';
import { isMonthly, parseMonths, type DcaCadence } from '../shared/dcaSchedule';
import { parseWindow } from '../shared/maWindows';
import { parseBandPercent } from '../shared/maBand';
import type { SignalChoice } from '../shared/signalSymbol';
import type { BacktestOptions } from '../shared/backtestOptions';

// 칸 아래 한 줄(오류는 실행을 막고 경고는 안 막음), 글 뒤에 작은 단추 하나
export interface FieldMessage {
  kind: 'error' | 'warning';
  text: string;
  action?: { label: string; onClick: () => void };
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

// 신호 종목은 다른 종목을 고른 때만 봄
export const signalIssue = (choice: SignalChoice, other: string, supportedSymbols: string[]): FieldMessage | null => {
  if (choice !== 'other') return null;
  return other.trim() ? symbolIssue(other, supportedSymbols) : error('신호 종목 비어 있음');
};

interface AmountCheck {
  byShares: boolean;
  amount: string;
  shares: string;
  // 칸 이름(비어 있음 앞말)
  label: string;
}

export const amountIssue = ({ byShares, amount, shares, label }: AmountCheck): FieldMessage | null => {
  if (byShares) {
    if (parseShares(shares) !== undefined) return null;
    return error(Number(shares) > MAX_SHARES ? `한 번에 ${formatNumber(MAX_SHARES)}주까지 계산 · 더 크면 금액으로` : '1 이상 정수');
  }
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

// 이동평균 기간 칸 오류(칸마다 2 ~ 250 정수, 짧은 기간이 긴 기간 이상이면 짧은 칸)
export const windowIssues = (short: string, long: string) => {
  const shortWindow = parseWindow(short);
  const longWindow = parseWindow(long);
  const range = error('2 ~ 250 사이 정수');
  const tooLong = shortWindow !== undefined && longWindow !== undefined && shortWindow >= longWindow;
  return {
    short: shortWindow === undefined ? range : tooLong ? error('긴 기간보다 짧게') : null,
    long: longWindow === undefined ? range : null,
  };
};

// 밴드 칸 오류(평균 기간 2 ~ 250 정수, 위 · 아래 비율 0 이상 100 미만)
export const bandIssues = (window: string, upper: string, lower: string) => {
  const rate = (text: string) => {
    if (!text.trim()) return error('% 비어 있음');
    return parseBandPercent(text) === undefined ? error('0 이상 100 미만') : null;
  };
  return {
    window: parseWindow(window) === undefined ? error('2 ~ 250 사이 정수') : null,
    upper: rate(upper),
    lower: rate(lower),
  };
};

// 비면 0
export const feeIssue = (fee: string): FieldMessage | null => {
  if (!fee.trim()) return null;
  const value = parseFloat(fee);
  if (isNaN(value)) return error('숫자로');
  return value >= 100 ? error('100 아래') : null;
};

// 비면 그 날짜 시장 환율
export const fxRateIssue = (rate: string): FieldMessage | null =>
  rate.trim() && !(parseFloat(rate) > 0) ? error('0 보다 크게') : null;

// 세부 설정 칸 오류(직접 입력 환율은 직접 입력일 때만)
export const detailIssues = (
  options: Pick<BacktestOptions, 'tradingFeeRate' | 'fxMode' | 'manualPurchaseFxRate' | 'manualCurrentFxRate'>,
) => {
  const manual = options.fxMode === 'manual';
  return {
    fee: feeIssue(options.tradingFeeRate),
    buyFx: manual ? fxRateIssue(options.manualPurchaseFxRate) : null,
    endFx: manual ? fxRateIssue(options.manualCurrentFxRate) : null,
  };
};

// 환율 기록이 있는 첫날
export const FX_RECORD_FROM = '2014-01-01';

// 그 날짜가 환율 기록 전이고 매수 환율을 적지 않았으면 기본 환율 안내
export const fxGapNotice = (
  from: string,
  options: Pick<BacktestOptions, 'fxMode' | 'manualPurchaseFxRate'>,
): FieldMessage | null => {
  if (!from || from >= FX_RECORD_FROM) return null;
  if (options.fxMode === 'manual' && parseFloat(options.manualPurchaseFxRate) > 0) return null;
  return warning('2014년 이전은 환율 기록이 없어 그 기간은 기본 환율로 계산');
};
