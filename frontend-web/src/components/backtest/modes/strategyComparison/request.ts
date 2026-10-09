import type { StrategyComparisonRequest } from '@services/api';
import { strategyByType } from '../../comparison/catalog';
import type { ComparisonStrategyType, StrategyParams } from '../../comparison/types';
import type { BuildResult, RunContext } from '../types';

export interface StrategyComparisonValues {
  symbol: string;
  startDate: string;
  endDate: string;
  investment: string;
  selectedStrategies: ComparisonStrategyType[];
  strategyParameters: Record<string, StrategyParams>;
  reinvestDividends: boolean;
  tradingFeeRate: string;
  dividendTax: boolean;
  fxMode: 'auto' | 'manual';
  manualPurchaseFxRate: string;
  manualCurrentFxRate: string;
}

// 전략 비교 요청. 검사에 걸리면 alert 문구를 돌려준다
export const buildStrategyComparisonRequest = (
  values: StrategyComparisonValues,
  ctx: RunContext,
): BuildResult<StrategyComparisonRequest> => {
  if (values.selectedStrategies.length < 2) {
    return { error: '최소 2개 이상의 전략을 선택해주세요.' };
  }

  if (new Date(values.startDate) >= new Date(values.endDate)) {
    return { error: '시작일은 종료일보다 빠른 날짜여야 합니다.' };
  }

  const common = { startDate: values.startDate, investment: values.investment };

  // 전략 파라미터 유효성 검사 (빈 값은 기본값으로 본다)
  for (const strategyType of values.selectedStrategies) {
    const message = strategyByType(strategyType).checkOnRun(
      values.strategyParameters[strategyType] || {},
      common,
    );
    if (message) return { error: message };
  }

  const strategies = values.selectedStrategies.map((strategyType) =>
    strategyByType(strategyType).toRequest(values.strategyParameters[strategyType] || {}, common),
  );

  // 종료일이 비어있을시 현재날짜로 변경
  const effectiveEndDate = values.endDate || ctx.today;

  const request: StrategyComparisonRequest = {
    symbol: values.symbol,
    startDate: values.startDate,
    endDate: effectiveEndDate,
    investmentAmount: parseFloat(values.investment),
    strategies,
    reinvestDividends: values.reinvestDividends,
    tradingFeeRate: parseFloat(values.tradingFeeRate) / 100,
    dividendTaxRate: values.dividendTax ? 0.15 : 0,
    userId: ctx.userId,
  };
  if (values.fxMode === 'manual') {
    request.purchaseFxRate = parseFloat(values.manualPurchaseFxRate);
    request.currentFxRate = parseFloat(values.manualCurrentFxRate);
  }
  return { request };
};
