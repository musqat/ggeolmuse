import { ConditionalFields } from './fields/ConditionalFields';
import type { ComparisonStrategyDef, StrategyParams } from './types';
import { DIRECTIONS, toDirection } from '../shared/priceDirection';
import { toSellRates } from '../shared/sellRates';
import { formatKrw } from '../../../utils/format';

const name = '조건부 매수';

const DEFAULTS: StrategyParams = { amountPerPurchase: '100000', maxPurchases: '', dropPercentage: '5', direction: 'DROP' };

// 기본값을 채운 칩 값(최대 횟수는 비우면 원금 한도까지)
const read = (params: StrategyParams) => {
  const p = { ...DEFAULTS, ...params };
  const max = (p.maxPurchases ?? '').trim();
  return {
    p,
    direction: toDirection(p.direction),
    amount: parseFloat(p.amountPerPurchase),
    max: max === '' ? undefined : Number(max),
    drop: parseFloat(p.dropPercentage),
  };
};

// 적은 익절 · 손절 비율(범위 검사는 check)
const sellRatesOf = (params: StrategyParams) => {
  const sell = toSellRates(params.takeProfit, params.stopLoss);
  return 'rates' in sell ? sell.rates : {};
};

export const conditionalStrategy: ComparisonStrategyDef = {
  type: 'CONDITIONAL_PURCHASE',
  name,
  defaultParams: DEFAULTS,
  check: (params, common) => {
    const { p, direction, amount, max, drop } = read(params);
    if (!(amount > 0)) return `${name}: 올바른 회당 금액을 입력해주세요.`;
    if (amount > parseFloat(common.investment)) return `${name}: 회당 금액이 원금보다 큽니다.`;
    if (max !== undefined && !(Number.isInteger(max) && max > 0)) {
      return `${name}: 최대 횟수는 비우거나 1 이상의 정수로 입력해주세요.`;
    }
    if (!(drop > 0 && drop <= 100)) return `${name}: ${DIRECTIONS[direction].rate}은 0~100 사이여야 합니다.`;
    const sell = toSellRates(p.takeProfit, p.stopLoss);
    return 'error' in sell ? `${name}: ${sell.error}` : null;
  },
  toRequest: (params) => {
    const { p, direction, amount, max, drop } = read(params);
    return {
      strategyType: 'CONDITIONAL_PURCHASE',
      name: 'CONDITIONAL_PURCHASE',
      amountPerPurchase: amount,
      direction,
      dropPercentage: drop / 100,
      ...(max !== undefined && { maxPurchases: max }),
      ...sellRatesOf(p),
    };
  },
  // 횟수는 원금 ÷ 회당 금액과 최대 횟수 가운데 작은 쪽
  summary: (params, common) => {
    const { p, direction, amount, max, drop } = read(params);
    const cap = Math.floor(parseFloat(common.investment) / amount);
    const parts = [
      `전날보다 ${drop}% ${DIRECTIONS[direction].verb} 때마다 ${formatKrw(amount)}`,
      max !== undefined && max < cap ? `최대 ${max}회` : `원금으로 최대 ${cap}회`,
    ];
    if (p.takeProfit?.trim()) parts.push(`익절 +${p.takeProfit.trim()}%`);
    if (p.stopLoss?.trim()) parts.push(`손절 -${p.stopLoss.trim()}%`);
    return { text: parts.join(' · ') };
  },
  Fields: ConditionalFields,
};
