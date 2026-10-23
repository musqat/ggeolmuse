import type { StrategyResponse } from '@services/api';
import type { PreparedRun } from './types';

interface StrategyRunInput<T extends object> {
  request: T;
  call: (request: T) => Promise<{ data: StrategyResponse }>;
  mode: 'dca' | 'conditional';
  fxMode: PreparedRun['fxMode'];
  failureMessage: string;
}

// 적립식 · 조건부 실행 준비(결과에 모드 태그, 이력은 전략 시뮬레이션)
export const strategyRun = <T extends object>({
  request,
  call,
  mode,
  fxMode,
  failureMessage,
}: StrategyRunInput<T>): PreparedRun => ({
  execute: async () => ({ ...(await call(request)).data, mode }),
  historyType: 'STRATEGY_SIMULATION',
  params: request,
  fxMode,
  failureMessage,
});
