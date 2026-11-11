import type { StrategyResponse } from '@services/api';
import type { PreparedRun } from './types';
import type { BandSetting } from '../shared/maBand';

interface StrategyRunInput<T extends PreparedRun['params']> {
  request: T;
  call: (request: T) => Promise<{ data: StrategyResponse }>;
  mode: 'dca' | 'conditional' | 'ma-cross' | 'ma-band';
  fxMode: PreparedRun['fxMode'];
  failureMessage: string;
  // 결과에 같이 담는 요청의 예산
  budget?: number;
  // 결과에 같이 담는 요청의 이동평균 짧은 · 긴 기간
  windows?: { short: number; long: number };
  // 결과에 같이 담는 요청의 밴드 평균 기간 · 위 · 아래 비율
  band?: BandSetting;
}

// 적립식 · 조건부 · 이동평균 · 밴드 실행 준비(결과에 모드 태그, 이력은 전략 시뮬레이션)
export const strategyRun = <T extends PreparedRun['params']>({
  request,
  call,
  mode,
  fxMode,
  failureMessage,
  budget,
  windows,
  band,
}: StrategyRunInput<T>): PreparedRun => ({
  execute: async () => ({
    ...(await call(request)).data,
    mode,
    ...(budget !== undefined && { budget }),
    ...(windows && { windows }),
    ...(band && { band }),
  }),
  historyType: 'STRATEGY_SIMULATION',
  params: request,
  fxMode,
  failureMessage,
});
