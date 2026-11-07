import { ConditionalFields } from './fields/ConditionalFields';
import type { ComparisonStrategyDef, StrategyParams } from './types';
import { DIRECTIONS, toDirection } from '../shared/priceDirection';
import { toSellRates } from '../shared/sellRates';

const name = '조건부 매수';

// 적은 익절 · 손절 비율(범위 검사는 checkOnRun)
const sellRatesOf = (params: StrategyParams) => {
  const sell = toSellRates(params.takeProfit, params.stopLoss);
  return 'rates' in sell ? sell.rates : {};
};

export const conditionalStrategy: ComparisonStrategyDef = {
  type: 'CONDITIONAL_PURCHASE',
  name,
  defaultParams: { dropPercentage: '5', direction: 'DROP' },
  checkOnSave: (params) =>
    !params.dropPercentage ? `${name}: ${DIRECTIONS[toDirection(params.direction)].rate}을 입력해주세요.` : null,
  checkOnRun: (params, common) => {
    // 설정되지 않은 경우 전략 투자금과 기본 비율 사용
    const totalInvestment = parseFloat(params.totalInvestment || common.investment || '0');
    const dropPercentage = parseFloat(params.dropPercentage || '5');
    if (!totalInvestment || totalInvestment <= 0) {
      return `${name}: 총 투자금이 유효하지 않습니다.`;
    }
    if (!dropPercentage || dropPercentage <= 0) {
      return `${name}: ${DIRECTIONS[toDirection(params.direction)].rate}이 유효하지 않습니다.`;
    }
    const sell = toSellRates(params.takeProfit, params.stopLoss);
    return 'error' in sell ? `${name}: ${sell.error}` : null;
  },
  toRequest: (params, common) => ({
    strategyType: 'CONDITIONAL_PURCHASE',
    name: 'CONDITIONAL_PURCHASE',
    totalInvestment: parseFloat(params.totalInvestment || common.investment),
    direction: toDirection(params.direction),
    dropPercentage: parseFloat(params.dropPercentage || '5') / 100,
    ...sellRatesOf(params),
  }),
  Fields: ConditionalFields,
};
