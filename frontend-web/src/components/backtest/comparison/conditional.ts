import { ConditionalFields } from './fields/ConditionalFields';
import type { ComparisonStrategyDef } from './types';

const name = '조건부 매수';

export const conditionalStrategy: ComparisonStrategyDef = {
  type: 'CONDITIONAL_PURCHASE',
  name,
  defaultParams: { dropPercentage: '5' },
  checkOnSave: (params) => (!params.dropPercentage ? `${name}: 하락률을 입력해주세요.` : null),
  checkOnRun: (params, common) => {
    // 설정되지 않은 경우 전략 투자금과 기본 하락률 사용
    const totalInvestment = parseFloat(params.totalInvestment || common.investment || '0');
    const dropPercentage = parseFloat(params.dropPercentage || '5');
    if (!totalInvestment || totalInvestment <= 0) {
      return `${name}: 총 투자금이 유효하지 않습니다.`;
    }
    if (!dropPercentage || dropPercentage <= 0) {
      return `${name}: 하락률이 유효하지 않습니다.`;
    }
    return null;
  },
  toRequest: (params, common) => ({
    strategyType: 'CONDITIONAL_PURCHASE',
    name: 'CONDITIONAL_PURCHASE',
    totalInvestment: parseFloat(params.totalInvestment || common.investment),
    dropPercentage: parseFloat(params.dropPercentage || '5') / 100,
  }),
  Fields: ConditionalFields,
};
