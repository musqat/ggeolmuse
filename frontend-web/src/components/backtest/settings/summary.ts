import type { PriceDirection } from '@services/api';
import { formatReadableWon } from '../../../utils/format';
import { DIRECTIONS } from '../shared/priceDirection';
import type { BacktestOptions } from '../shared/backtestOptions';

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

// 익절 · 손절(평균 단가 기준 전량 매도), 둘 다 비면 빈 글
export const sellText = (takeProfit: string, stopLoss: string) => {
  const parts = [takeProfit.trim() && `+${takeProfit.trim()}% 익절`, stopLoss.trim() && `-${stopLoss.trim()}% 손절`].filter(Boolean);
  return parts.length ? `${parts.join(' · ')}(평균 단가 기준 전량)` : '';
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
  const { percent, direction, unit, takeProfit, stopLoss } = summary;
  const parts = [
    `전날보다 ${percent.trim() || '?'}% ${DIRECTIONS[direction].verb} 때마다 ${unitText(unit)}`,
    limitText(summary),
    sellText(takeProfit, stopLoss),
  ];
  return parts.filter(Boolean).join(' · ');
};

type DetailInput = Pick<BacktestOptions, 'tradingFeeRate' | 'dividendTax' | 'reinvestDividends' | 'fxMode'>;

// 세부 설정 값 글(기본값도 값으로, changed 는 기본값과 다른지, 재투자가 막혔으면 현금)
export const detailParts = (options: DetailInput, reinvestLocked: boolean) => {
  const fee = parseFloat(options.tradingFeeRate) || 0;
  const reinvest = options.reinvestDividends && !reinvestLocked;
  const manual = options.fxMode === 'manual';
  return [
    { text: `배당세 ${options.dividendTax ? '15%' : '끔'}`, changed: !options.dividendTax },
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
