import { simpleStrategy } from './simple';
import { dcaStrategy } from './dca';
import { conditionalStrategy } from './conditional';
import type { ComparisonStrategyDef, ComparisonStrategyType } from './types';

// 전략 비교에 나오는 전략. 버튼도 이 순서로 그린다
export const COMPARISON_STRATEGIES: ComparisonStrategyDef[] = [
  simpleStrategy,
  dcaStrategy,
  conditionalStrategy,
];

export const strategyByType = (type: ComparisonStrategyType): ComparisonStrategyDef => {
  const def = COMPARISON_STRATEGIES.find((s) => s.type === type);
  if (!def) throw new Error(`알 수 없는 전략: ${type}`);
  return def;
};

export const STRATEGY_NAMES: Record<string, string> = Object.fromEntries(
  COMPARISON_STRATEGIES.map((s) => [s.type, s.name]),
);
