import type { StrategyComparisonRequest } from '@services/api';
import { strategyByType } from '../../comparison/catalog';
import type { ComparisonStrategyType, StrategyParams } from '../../comparison/types';
import type { BuildResult, RunContext } from '../types';
import { toOptionFields, type BacktestOptions } from '../../shared/backtestOptions';
import { hasSellRates } from '../../shared/sellRates';

export interface StrategyComparisonValues extends BacktestOptions {
  symbol: string;
  startDate: string;
  endDate: string;
  investment: string;
  selectedStrategies: ComparisonStrategyType[];
  strategyParameters: Record<string, StrategyParams>;
}

// 고른 조건부 매수에 익절 · 손절을 적었는지(배당 재투자와 함께 못 씀)
export const conditionalSells = (
  selectedStrategies: ComparisonStrategyType[],
  strategyParameters: Record<string, StrategyParams>,
): boolean => {
  const params = strategyParameters.CONDITIONAL_PURCHASE;
  return selectedStrategies.includes('CONDITIONAL_PURCHASE') && hasSellRates(params?.takeProfit, params?.stopLoss);
};

// 전략 비교 요청(검사에 걸리면 alert 문구)
export const buildStrategyComparisonRequest = (
  values: StrategyComparisonValues,
  ctx: RunContext,
): BuildResult<StrategyComparisonRequest> => {
  if (values.selectedStrategies.length < 2) {
    return { error: '최소 2개 이상의 전략을 선택해주세요.' };
  }

  // 빈 종료일은 오늘로 채워 검사
  const effectiveEndDate = values.endDate || ctx.today;
  if (values.startDate >= effectiveEndDate) {
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

  const request: StrategyComparisonRequest = {
    symbol: values.symbol,
    startDate: values.startDate,
    endDate: effectiveEndDate,
    investmentAmount: parseFloat(values.investment),
    strategies,
    ...toOptionFields(values),
  };
  if (conditionalSells(values.selectedStrategies, values.strategyParameters)) {
    request.reinvestDividends = false;
  }
  return { request };
};
