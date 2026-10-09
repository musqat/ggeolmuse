import { DcaFields } from './fields/DcaFields';
import type { ComparisonStrategyDef } from './types';

const name = '적립식';

export const dcaStrategy: ComparisonStrategyDef = {
  type: 'DCA',
  name,
  defaultParams: { monthlyAmount: '100000', purchaseDay: '15', investmentInterval: '1' },
  checkOnSave: (params) =>
    !params.monthlyAmount || !params.purchaseDay ? `${name}: 월 투자금과 매수일을 입력해주세요.` : null,
  checkOnRun: (params) => {
    // 모달에서 설정하지 않은 경우 기본값 사용
    const monthlyAmount = parseFloat(params.monthlyAmount || '100000');
    const purchaseDay = parseInt(params.purchaseDay || '15');
    if (!monthlyAmount || monthlyAmount <= 0) {
      return `${name}: 월 투자금이 유효하지 않습니다.`;
    }
    if (!purchaseDay || purchaseDay < 1 || purchaseDay > 31) {
      return `${name}: 매수일이 유효하지 않습니다 (1-31).`;
    }
    return null;
  },
  toRequest: (params, common) => ({
    strategyType: 'DCA',
    name: 'DCA',
    monthlyAmount: parseFloat(params.monthlyAmount || '100000'),
    purchaseDay: parseInt(params.purchaseDay || '15'),
    investmentInterval: parseInt(params.investmentInterval || '1'),
    totalInvestmentLimit: parseFloat(common.investment),
  }),
  Fields: DcaFields,
};
