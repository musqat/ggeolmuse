import type { StrategyComparisonRequest } from '@services/api';
import { strategyByType } from '../../comparison/catalog';
import type { ComparisonStrategyType, StrategyParams } from '../../comparison/types';

// 전략 비교 칩 하나(같은 종류도 여럿, name 은 화면 이름)
export interface StrategyChip {
  id: string;
  type: ComparisonStrategyType;
  name: string;
  params: StrategyParams;
}

// 요청 이름: 그 종류의 첫 칩은 종류, 번호가 있는 칩은 화면 이름
export const requestName = (chip: StrategyChip) => (chip.name === strategyByType(chip.type).name ? chip.type : chip.name);
import type { BuildResult, RunContext } from '../types';
import { toOptionFields, type BacktestOptions } from '../../shared/backtestOptions';

export interface StrategyComparisonValues extends BacktestOptions {
  symbol: string;
  startDate: string;
  endDate: string;
  investment: string;
  chips: StrategyChip[];
}

// 전략 비교 요청(검사에 걸리면 alert 문구), 금액은 전략마다 원금
export const buildStrategyComparisonRequest = (
  values: StrategyComparisonValues,
  ctx: RunContext,
): BuildResult<StrategyComparisonRequest> => {
  if (values.chips.length < 2) {
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

  // 칩마다 검사(없는 칸은 기본값으로 봄)
  for (const chip of values.chips) {
    const message = strategyByType(chip.type).check(chip.params, common);
    if (message) return { error: `${chip.name}: ${message}` };
  }

  const strategies = values.chips.map((chip) => ({
    ...strategyByType(chip.type).toRequest(chip.params, common),
    name: requestName(chip),
  }));

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
