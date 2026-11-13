import type { ExecutionTiming } from '@services/api';

export const EXECUTION_TIMINGS: readonly ExecutionTiming[] = ['SAME_DAY_CLOSE', 'NEXT_DAY_OPEN'];

export const EXECUTION_LABELS: Record<ExecutionTiming, string> = {
  SAME_DAY_CLOSE: '신호 난 날 종가',
  NEXT_DAY_OPEN: '다음 거래일 시가',
};

export const EXECUTION_OPTIONS = EXECUTION_TIMINGS.map((value) => ({ value, label: EXECUTION_LABELS[value] }));

// 요청에 싣는 체결 시점(신호 난 날 종가는 비움)
export const executionTimingFields = (timing: ExecutionTiming | undefined): { executionTiming?: ExecutionTiming } =>
  timing === 'NEXT_DAY_OPEN' ? { executionTiming: timing } : {};
