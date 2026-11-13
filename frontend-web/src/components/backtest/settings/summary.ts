import type { PortfolioRebalance, PortfolioStrategyType, PriceDirection } from '@services/api';
import { formatReadableWon } from '../../../utils/format';
import { DIRECTIONS } from '../shared/priceDirection';
import type { BacktestOptions } from '../shared/backtestOptions';
import type { TakeProfitStep } from '../shared/partialTakeProfit';
import { STRATEGY_LABELS } from '../shared/portfolioRows';

// 한 회차에 사는 단위와 그 돈(주식 수면 시작일 시세로 추정, 모르면 perRound 없음, defaultFx 는 기본 환율로 셈)
export interface RoundUnit {
  byShares: boolean;
  amount: string;
  shares: string;
  perRound?: number;
  defaultFx?: boolean;
}

// 주식 수 추정 금액의 기준
export const estimateBasis = (defaultFx?: boolean) => (defaultFx ? '(시작일 값 · 기본 환율 기준)' : '(시작일 값 기준)');

const roundCost = (unit: RoundUnit) => (unit.byShares ? unit.perRound : parseFloat(unit.amount) || 0);
const unitText = (unit: RoundUnit) => (unit.byShares ? `${unit.shares}주` : formatReadableWon(parseFloat(unit.amount)));
const estimate = (unit: RoundUnit) => (unit.byShares ? estimateBasis(unit.defaultFx) : '');
// 비면 undefined
const optional = (value: string) => (value.trim() ? parseFloat(value) : undefined);

export const simpleHow = (unit: RoundUnit) => {
  if (!unit.byShares) return `시작일에 ${unitText(unit)} 매수`;
  return `시작일에 ${unitText(unit)} 매수${unit.perRound ? ` ≈ ${formatReadableWon(unit.perRound)}${estimate(unit)}` : ''}`;
};

interface DcaSummary {
  unit: RoundUnit;
  // 주기 · 요일 · 며칠
  cadenceLabel: string;
  rounds: number;
  daily: boolean;
  // 주식 수 단위의 예산(비면 제한 없음)
  budget: string;
}

// 예산으로 살 수 있는 회차가 기간 회차보다 적으면 나머지는 미매수
export const dcaHow = ({ unit, cadenceLabel, rounds, daily, budget }: DcaSummary) => {
  const head = `${cadenceLabel} ${unitText(unit)}`;
  const about = daily ? '약 ' : '';
  const cost = roundCost(unit);
  if (!cost) return `${head} · ${about}${rounds}회`;
  const limit = optional(budget);
  const affordable = limit ? Math.min(rounds, Math.floor(limit / cost)) : rounds;
  if (affordable < rounds) {
    return `${head} · ${about}${rounds}회 중 ${affordable}회 ≈ ${formatReadableWon(cost * affordable)}${estimate(unit)} · 나머지 미매수`;
  }
  return `${head} · ${about}${rounds}회 ≈ ${formatReadableWon(cost * rounds)}${estimate(unit)}`;
};

interface PortfolioSummary {
  // 줄마다 전략과 금액(원, 적립식은 회당, 이동평균 · 밴드는 첫 매수 신호에)
  lines: { strategy: PortfolioStrategyType; amount: number }[];
  cadenceLabel: string;
  rounds: number;
  daily: boolean;
  // 줄 익절 · 손절 글(없으면 빈 글)
  sell?: string;
  rebalance: PortfolioRebalance;
}

const REBALANCE_TEXT: Record<PortfolioRebalance, string> = {
  NONE: '',
  YEARLY: '매년 처음 비중으로 리밸런싱',
  QUARTERLY: '분기마다 처음 비중으로 리밸런싱',
  MONTHLY: '매달 처음 비중으로 리밸런싱',
};

const SIGNALS: readonly PortfolioStrategyType[] = ['MA_CROSS', 'MA_BAND'];

// 포트폴리오: 종목 수와 전략마다 넣는 합계(한 번에 · 적립식 요약 · 이동평균 · 밴드는 첫 매수 신호에, 전략이 섞이면 전략마다 줄 수), 줄 익절 · 손절 글, 리밸런싱하면 그 주기(이동평균 · 밴드 줄은 빼고)
export const portfolioHow = ({ lines, cadenceLabel, rounds, daily, sell, rebalance }: PortfolioSummary) => {
  const pick = (strategies: readonly PortfolioStrategyType[]) => lines.filter((line) => strategies.includes(line.strategy));
  const total = (picked: PortfolioSummary['lines']) => picked.reduce((sum, line) => sum + line.amount, 0);
  const mixed = new Set(lines.map((line) => line.strategy)).size > 1;
  const lump = pick(['LUMP_SUM']);
  const dca = pick(['DCA']);
  const signals = pick(SIGNALS);
  const signalNames = SIGNALS.filter((strategy) => pick([strategy]).length).map((strategy) => STRATEGY_LABELS[strategy]);
  const unit = { byShares: false, amount: String(total(dca)), shares: '' };
  const rebalanced = REBALANCE_TEXT[rebalance] && signals.length < lines.length;
  return [
    `종목 ${lines.length}개`,
    lump.length > 0 && `한 번에 ${mixed ? `${lump.length}개 ` : ''}${formatReadableWon(total(lump))}`,
    dca.length > 0 && `${mixed ? `적립식 ${dca.length}개 ` : ''}${dcaHow({ unit, cadenceLabel, rounds, daily, budget: '' })}`,
    signals.length > 0 && `${signalNames.join(', ')} ${signals.length}개 첫 매수 신호에 ${formatReadableWon(total(signals))}`,
    sell,
    rebalanced && `${REBALANCE_TEXT[rebalance]}${signals.length ? '(이동평균 교차 · 밴드 빼고)' : ''}`,
  ]
    .filter(Boolean)
    .join(' · ');
};

// 익절 · 손절(평균 단가 기준 전량 매도), 둘 다 비면 빈 글
export const sellText = (takeProfit: string, stopLoss: string) => {
  const parts = [takeProfit.trim() && `+${takeProfit.trim()}% 익절`, stopLoss.trim() && `-${stopLoss.trim()}% 손절`].filter(Boolean);
  return parts.length ? `${parts.join(' · ')}(평균 단가 기준 전량)` : '';
};

// 트레일링 스탑(종가 고점 대비, 팔 비율이 비거나 100 이면 전량), 하락을 비우면 빈 글
export const trailingText = (stop: string, sell: string) => {
  const drop = stop.trim();
  const share = sell.trim();
  if (!drop) return '';
  return !share || Number(share) >= 100
    ? `고점 대비 하락 매도 -${drop}%(전량)`
    : `고점 대비 하락 매도 -${drop}%(보유의 ${share}%씩)`;
};

// 부분 익절 단계마다 평균 단가 대비 수익률에 보유의 팔 비율(100 이상은 전량, 빈 칸은 ?), 단계가 없으면 빈 글
export const partialTakeProfitText = (steps: TakeProfitStep[]) => {
  if (!steps.length) return '';
  const parts = steps.map(({ gain, sell }) => {
    const share = sell.trim();
    return `+${gain.trim() || '?'}%에 ${share && Number(share) >= 100 ? '전량' : `보유의 ${share || '?'}%`}`;
  });
  return `부분 익절 ${parts.join(', ')}(평균 단가 기준)`;
};

interface ConditionalSummary {
  percent: string;
  direction: PriceDirection;
  unit: RoundUnit;
  budget: string;
  maxPurchases: string;
  // 기간의 평일 수(한도가 없을 때 많아야 살 횟수)
  weekdays: number;
  takeProfit: string;
  stopLoss: string;
  // 트레일링 스탑 · 부분 익절 글(끄면 빈 글)
  trailing?: string;
  partial?: string;
}

// 횟수는 예산 ÷ 한 번 살 돈과 최대 횟수 가운데 먼저 닿는 쪽
const limitText = ({ unit, budget, maxPurchases, weekdays }: ConditionalSummary) => {
  const cost = roundCost(unit);
  const limit = optional(budget);
  const max = optional(maxPurchases);
  const won = (count: number) => `${formatReadableWon(cost! * count)}${estimate(unit)}`;
  if (!cost) return max ? `최대 ${max}회` : limit ? `예산 ${formatReadableWon(limit)}` : '횟수 제한 없음';
  const byBudget = limit ? Math.floor(limit / cost) : Infinity;
  if (byBudget === Infinity && max === undefined) return `횟수 제한 없음 · 많아야 약 ${weekdays}회 ≈ ${won(weekdays)}`;
  if (max !== undefined && max <= byBudget) return `최대 ${max}회 ≈ ${won(max)}${limit ? `(예산 ${formatReadableWon(limit)})` : ''}`;
  if (byBudget > 0) return `예산으로 최대 ${byBudget}회 ≈ ${won(byBudget)}${max !== undefined ? `(최대 횟수 ${max})` : ''}`;
  return `예산 ${formatReadableWon(limit!)}`;
};

export const conditionalHow = (summary: ConditionalSummary) => {
  const { percent, direction, unit, takeProfit, stopLoss, trailing, partial } = summary;
  const parts = [
    `전날보다 ${percent.trim() || '?'}% ${DIRECTIONS[direction].verb} 때마다 ${unitText(unit)}`,
    limitText(summary),
    sellText(takeProfit, stopLoss),
    trailing,
    partial,
  ];
  return parts.filter(Boolean).join(' · ');
};

// 이동평균 교차 규칙(빈 기간은 ?)
export const maCrossText = (short: string, long: string) =>
  `${short.trim() || '?'}일 선이 ${long.trim() || '?'}일 선을 넘으면 가진 돈 전부로 사고, 아래로 내려가면 전부 팜`;

// 이동평균 교차 모드 설명(빈 기간은 ?)
export const maCrossAbout = (short: string, long: string) =>
  `최근 ${short.trim() || '?'}일 평균 가격이 ${long.trim() || '?'}일 평균보다 높아지면 매수, 낮아지면 매도`;

// 원금과 따로 고른 신호 종목
const signalLead = (amount: string, signal?: string) =>
  [`원금 ${formatReadableWon(parseFloat(amount))}`, signal && `신호 ${signal}`].filter(Boolean).join(' · ');

// 원금 · 신호 종목과 신호 규칙 뒤에 트레일링 스탑 · 부분 익절 글(끄면 빈 글)
const signalHow = (amount: string, signal: string | undefined, rule: string, ...sells: (string | undefined)[]) =>
  [signalLead(amount, signal), rule, ...sells].filter(Boolean).join(' · ');

export const maCrossHow = ({
  amount,
  short,
  long,
  signal,
  trailing,
  partial,
}: {
  amount: string;
  short: string;
  long: string;
  signal?: string;
  trailing?: string;
  partial?: string;
}) => signalHow(amount, signal, maCrossText(short, long), trailing, partial);

// 이동평균 밴드 규칙(빈 칸은 ?)
export const maBandText = (window: string, upper: string, lower: string) =>
  `${window.trim() || '?'}일선 +${upper.trim() || '?'}% 위로 오르면 가진 돈 전부로 사고, -${lower.trim() || '?'}% 아래로 내려가면 전부 팜`;

// 이동평균 밴드 모드 설명(빈 칸은 ?)
export const maBandAbout = (window: string, upper: string, lower: string) =>
  `주가가 ${window.trim() || '?'}일 평균 가격보다 ${upper.trim() || '?'}% 넘게 오르면 매수, ${lower.trim() || '?'}% 넘게 내려가면 매도`;

export const maBandHow = ({
  amount,
  window,
  upper,
  lower,
  signal,
  trailing,
  partial,
}: {
  amount: string;
  window: string;
  upper: string;
  lower: string;
  signal?: string;
  trailing?: string;
  partial?: string;
}) => signalHow(amount, signal, maBandText(window, upper, lower), trailing, partial);

type DetailInput = Pick<
  BacktestOptions,
  'periodEnd' | 'tradingFeeRate' | 'dividendTax' | 'capitalGainsTax' | 'reinvestDividends' | 'fxMode'
>;

// 세부 설정 값 글(기본값도 값으로, changed 는 기본값과 다른지, 재투자가 막혔으면 현금)
export const detailParts = (options: DetailInput, reinvestLocked: boolean) => {
  const fee = parseFloat(options.tradingFeeRate) || 0;
  const reinvest = options.reinvestDividends && !reinvestLocked;
  const manual = options.fxMode === 'manual';
  const sellAtEnd = options.periodEnd === 'SELL_AT_END';
  return [
    { text: sellAtEnd ? '마지막 거래일에 팔기' : '끝까지 보유', changed: sellAtEnd },
    { text: `배당세 ${options.dividendTax ? '15%' : '끔'}`, changed: !options.dividendTax },
    { text: `양도세 ${options.capitalGainsTax ? '22%' : '끔'}`, changed: !options.capitalGainsTax },
    { text: `배당 ${reinvest ? '재투자' : '현금'}`, changed: reinvest },
    { text: `수수료 ${options.tradingFeeRate.trim() || 0}%`, changed: fee > 0 },
    { text: `환율 ${manual ? '직접 입력' : '자동'}`, changed: manual },
  ];
};

// 세부 설정 가운데 기본값과 다른 것
export const changedOptions = (options: DetailInput, reinvestLocked: boolean): string[] =>
  detailParts(options, reinvestLocked)
    .filter((part) => part.changed)
    .map((part) => part.text);
