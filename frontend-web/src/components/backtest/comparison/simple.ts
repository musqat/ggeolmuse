import { SimpleFields } from './fields/SimpleFields';
import type { ComparisonStrategyDef } from './types';

// 공통 시작일에 한 번 산다. 채울 파라미터가 없다
export const simpleStrategy: ComparisonStrategyDef = {
  type: 'SIMPLE',
  name: '단순 매수',
  defaultParams: {},
  checkOnSave: () => null,
  checkOnRun: () => null,
  toRequest: (_params, common) => ({
    strategyType: 'SIMPLE',
    name: 'SIMPLE',
    purchaseDate: common.startDate,
  }),
  Fields: SimpleFields,
};
