import { formatNumber, formatReadableWon } from '../../../utils/format';
import { MAX_SHARES, parseShares } from '../shared/amountUnit';
import { isMonthly, parseMonths, type DcaCadence } from '../shared/dcaSchedule';
import { parseWindow } from '../shared/maWindows';
import { parseBandPercent, toRate } from '../shared/maBand';
import type { SignalChoice } from '../shared/signalSymbol';
import { stepRate, type TakeProfitStep } from '../shared/partialTakeProfit';
import type { BacktestOptions } from '../shared/backtestOptions';
import type { PortfolioAllocation } from '@services/api';
import { rowAmount, rowSymbol, rowWeight, type PortfolioRow } from '../shared/portfolioRows';

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

// 금액 칸 검사에 원 미만 금액 오류를 더함
export const wonIssue = (amount: string, label: string): FieldMessage | null =>
  amountIssue({ byShares: false, amount, shares: '', label }) ??
  (Number.isInteger(Number(amount)) ? null : error('원 단위로'));

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

// 요청 비율로 바꿔도 0 · 1 이 되지 않는 퍼센트 끝값(범위 안 값이 반올림으로 밖이 될 때의 칸 아래 글)
const RATE_MIN_PERCENT = '0.000001';
const RATE_MAX_PERCENT = '99.999999';

// 트레일링 스탑 칸 오류(하락 0 초과 100 미만, 팔 비율은 하락과 함께 0 초과 100 이하, 둘 다 비면 끔, 요청 비율로 바꾼 값으로 봄)
export const trailingIssues = (stop: string, sell: string): { stop: FieldMessage | null; sell: FieldMessage | null } => {
  const stopValue = Number(stop.trim());
  const sellValue = Number(sell.trim());
  const stopRate = toRate(stopValue);
  const sellRate = toRate(sellValue);
  const stopRange =
    stopValue > 0 && stopValue < 100 ? `${RATE_MIN_PERCENT} 이상 ${RATE_MAX_PERCENT} 이하` : '0 초과 100 미만';
  const sellRange = sellValue > 0 && sellValue <= 100 ? `${RATE_MIN_PERCENT} 이상 100 이하` : '0 초과 100 이하';
  const sellMessage = !stop.trim() ? error('하락 비율과 함께') : sellRate > 0 && sellRate <= 1 ? null : error(sellRange);
  return {
    stop: stop.trim() && !(stopRate > 0 && stopRate < 1) ? error(stopRange) : null,
    sell: sell.trim() ? sellMessage : null,
  };
};

// 부분 익절 단계마다 칸 오류(수익률은 0 초과로 앞 단계들보다 크게, 팔 비율은 0 초과 100 이하, 요청 비율로 바꾼 값으로 봄)
export const partialTakeProfitIssues = (
  steps: TakeProfitStep[],
): { gain: FieldMessage | null; sell: FieldMessage | null }[] => {
  let highest = 0;
  let highestValue = 0;
  return steps.map((step, index) => {
    const n = index + 1;
    const gainValue = Number(step.gain.trim());
    const sellValue = Number(step.sell.trim());
    const gainRate = stepRate(step.gain);
    const sellRate = stepRate(step.sell);
    const gain = !step.gain.trim()
      ? error(`${n}단계 수익률 비어 있음`)
      : !(gainRate > 0)
        ? error(gainValue > 0 ? `${n}단계 수익률 ${RATE_MIN_PERCENT} 이상` : `${n}단계 수익률 0 초과`)
        : gainRate <= highest
          ? error(
              gainValue > highestValue
                ? `${n}단계 수익률 앞 단계보다 ${RATE_MIN_PERCENT} 이상 크게`
                : `${n}단계 수익률 앞 단계보다 크게`,
            )
          : null;
    if (gainRate > highest) {
      highest = gainRate;
      highestValue = gainValue;
    }
    const sell = !step.sell.trim()
      ? error(`${n}단계 팔 비율 비어 있음`)
      : sellRate > 0 && sellRate <= 1
        ? null
        : error(
            sellValue > 0 && sellValue <= 100
              ? `${n}단계 팔 비율 ${RATE_MIN_PERCENT} 이상 100 이하`
              : `${n}단계 팔 비율 0 초과 100 이하`,
          );
    return { gain, sell };
  });
};

// 포트폴리오 줄 하나의 종목 칸 · 금액(비율) 칸 · 익절 칸 · 손절 칸 · 이동평균 두 기간 칸 · 밴드 세 칸 오류
export interface PortfolioRowMessages {
  symbol: FieldMessage | null;
  value: FieldMessage | null;
  takeProfit: FieldMessage | null;
  stopLoss: FieldMessage | null;
  shortWindow: FieldMessage | null;
  longWindow: FieldMessage | null;
  bandWindow: FieldMessage | null;
  upper: FieldMessage | null;
  lower: FieldMessage | null;
}

// 줄 칸 오류를 칸 차례로(종목 · 금액 · 익절 · 손절 · 이동평균 · 밴드)
export const portfolioRowMessageList = (messages: PortfolioRowMessages): (FieldMessage | null)[] => [
  messages.symbol,
  messages.value,
  messages.takeProfit,
  messages.stopLoss,
  messages.shortWindow,
  messages.longWindow,
  messages.bandWindow,
  messages.upper,
  messages.lower,
];

// 포트폴리오 줄 익절 · 손절 칸 오류(비면 안 함, 0 초과, 손절은 100 미만)
const rowSellIssues = (row: PortfolioRow, n: string) => {
  const notPositive = (text: string) => text.trim() && !(parseFloat(text) > 0);
  return {
    takeProfit: notPositive(row.takeProfit) ? error(`${n} 익절 0 보다 크게`) : null,
    stopLoss: notPositive(row.stopLoss)
      ? error(`${n} 손절 0 보다 크게`)
      : row.stopLoss.trim() && parseFloat(row.stopLoss) >= 100
        ? error(`${n} 손절 100 아래`)
        : null,
  };
};

// 포트폴리오 줄 이동평균 · 밴드 칸 오류(그 전략 줄만, 기간은 2 ~ 250 정수, 짧은 기간은 긴 기간보다 짧게, 비율은 0 이상 100 미만)
const rowSignalIssues = (row: PortfolioRow, n: string) => {
  const none = { shortWindow: null, longWindow: null, bandWindow: null, upper: null, lower: null };
  const prefixed = (name: string, message: FieldMessage | null) => message && error(`${n} ${name} ${message.text}`);
  if (row.strategy === 'MA_CROSS') {
    const windows = windowIssues(row.shortWindow, row.longWindow);
    return { ...none, shortWindow: prefixed('짧은 기간', windows.short), longWindow: prefixed('긴 기간', windows.long) };
  }
  if (row.strategy !== 'MA_BAND') return none;
  const band = bandIssues(row.bandWindow, row.upper, row.lower);
  // 빈 비율 칸은 칸 이름 뒤 "비어 있음"
  const rate = (name: string, text: string, message: FieldMessage | null) =>
    message && error(`${n} ${name} ${text.trim() ? message.text : '비어 있음'}`);
  return {
    ...none,
    bandWindow: prefixed('밴드 기간', band.window),
    upper: rate('위 비율', row.upper, band.upper),
    lower: rate('아래 비율', row.lower, band.lower),
  };
};

// 포트폴리오 줄마다 칸 오류(종목은 비었거나 목록에 없거나 앞 줄과 대문자로 같으면, 금액 방식은 0 초과 원 단위 금액 · 비율 방식은 0 초과 100 이하 비율, 익절 · 손절은 적은 때만, 이동평균 · 밴드 칸은 그 전략 줄만)
export const portfolioRowIssues = (
  rows: PortfolioRow[],
  allocation: PortfolioAllocation,
  supportedSymbols: string[],
): PortfolioRowMessages[] =>
  rows.map((row, index) => {
    const n = `${index + 1}번 줄`;
    const listed = supportedSymbols.length === 0 || supportedSymbols.includes(rowSymbol(row));
    const repeated = rows.slice(0, index).some((before) => rowSymbol(before) === rowSymbol(row));
    const symbol = !row.symbol.trim()
      ? error(`${n} 종목 비어 있음`)
      : !listed
        ? error(`${n} 종목 목록에서 고르기`)
        : repeated
          ? error(`${n} 종목이 앞 줄과 같음`)
          : null;
    const others = { ...rowSellIssues(row, n), ...rowSignalIssues(row, n) };
    if (allocation === 'AMOUNT') {
      const amount = rowAmount(row);
      const value = !row.amount.trim()
        ? error(`${n} 금액 비어 있음`)
        : !(amount > 0)
          ? error(`${n} 금액 0 보다 크게`)
          : Number.isInteger(amount)
            ? null
            : error(`${n} 금액 원 단위로`);
      return { symbol, value, ...others };
    }
    const weight = rowWeight(row);
    const value = !row.weight.trim()
      ? error(`${n} 비율 비어 있음`)
      : weight > 0 && weight <= 100
        ? null
        : error(`${n} 비율 0 초과 100 이하`);
    return { symbol, value, ...others };
  });

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

// 밴드 칸 오류(평균 기간 2 ~ 250 정수, 위 · 아래 비율 0 이상 100 미만, 비율은 요청 비율로 바꾼 값으로 봄)
export const bandIssues = (window: string, upper: string, lower: string) => {
  const rate = (text: string) => {
    if (!text.trim()) return error('% 비어 있음');
    if (parseBandPercent(text) !== undefined) return null;
    const value = Number(text.trim());
    return error(value >= 0 && value < 100 ? `0 이상 ${RATE_MAX_PERCENT} 이하` : '0 이상 100 미만');
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
