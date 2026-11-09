import type { StrategyResponse } from '@services/api';
import type { PreparedRun } from './types';

interface StrategyRunInput<T extends PreparedRun['params']> {
  request: T;
  call: (request: T) => Promise<{ data: StrategyResponse }>;
  mode: 'dca' | 'conditional';
  fxMode: PreparedRun['fxMode'];
  failureMessage: string;
  // 결과에 같이 담는 요청의 예산
  budget?: number;
}

// 적립식 · 조건부 실행 준비(결과에 모드 태그, 이력은 전략 시뮬레이션)
export const strategyRun = <T extends PreparedRun['params']>({
  request,
  call,
  mode,
  fxMode,
  failureMessage,
  budget,
}: StrategyRunInput<T>): PreparedRun => ({
  execute: async () => ({ ...(await call(request)).data, mode, ...(budget !== undefined && { budget }) }),
  historyType: 'STRATEGY_SIMULATION',
  params: request,
  fxMode,
  failureMessage,
});
