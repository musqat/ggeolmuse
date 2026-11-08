import type { ComparisonStrategyDef } from './types';
import { formatReadableWon } from '../../../utils/format';

// 공통 시작일에 원금으로 한 번 매수(채울 파라미터 없음)
export const simpleStrategy: ComparisonStrategyDef = {
  type: 'SIMPLE',
  name: '단순 매수',
  defaultParams: {},
  check: () => null,
  toRequest: (_params, common) => ({
    strategyType: 'SIMPLE',
    name: 'SIMPLE',
    purchaseDate: common.startDate,
  }),
  summary: (_params, common) => ({ text: `시작일에 ${formatReadableWon(parseFloat(common.investment) || 0)} 매수` }),
};
