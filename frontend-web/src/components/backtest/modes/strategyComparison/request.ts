import type { StrategyComparisonRequest } from '@services/api';
import { strategyByType } from '../../comparison/catalog';
import type { ComparisonStrategyType, StrategyParams } from '../../comparison/types';
import type { BuildResult, RunContext } from '../types';
import { toOptionFields, type BacktestOptions } from '../../shared/backtestOptions';

export interface StrategyComparisonValues extends BacktestOptions {
  symbol: string;
  startDate: string;
  endDate: string;
  investment: string;
  selectedStrategies: ComparisonStrategyType[];
  strategyParameters: Record<string, StrategyParams>;
}

// 전략 비교 요청(검사에 걸리면 alert 문구), 금액은 전략마다 원금
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
  const principal = parseFloat(values.investment);
  if (!(principal > 0)) {
    return { error: '올바른 원금을 입력해주세요.' };
  }

  const common = { startDate: values.startDate, endDate: effectiveEndDate, investment: values.investment };

  // 고른 칩마다 검사(없는 칸은 기본값으로 봄)
  for (const strategyType of values.selectedStrategies) {
    const message = strategyByType(strategyType).check(values.strategyParameters[strategyType] || {}, common);
    if (message) return { error: message };
  }

  const strategies = values.selectedStrategies.map((strategyType) =>
    strategyByType(strategyType).toRequest(values.strategyParameters[strategyType] || {}, common),
  );

  return {
    request: {
      symbol: values.symbol,
      startDate: values.startDate,
      endDate: effectiveEndDate,
      investmentAmount: principal,
      strategies,
      ...toOptionFields(values),
    },
  };
};
