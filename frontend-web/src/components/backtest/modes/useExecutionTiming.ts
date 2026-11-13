import { useState } from 'react';
import type { ExecutionTiming } from '@services/api';
import { EXECUTION_TIMINGS } from '../shared/executionTiming';
import { executionText } from '../settings/summary';
import { oneOf, pickValues } from '../settings/snapshot';
import type { ExecutionTimingFieldProps } from '../settings/ExecutionTimingField';

const DEFAULTS = { executionTiming: 'SAME_DAY_CLOSE' };

// 이동평균 · 밴드 모드의 체결 시점(칸 · 요약 글 · 요청 값 · 직전 설정에 저장하는 값)
export const useExecutionTiming = () => {
  const [timing, setTiming] = useState<ExecutionTiming>('SAME_DAY_CLOSE');
  const field: ExecutionTimingFieldProps = { value: timing, onChange: setTiming };

  return {
    field,
    timing,
    text: executionText(timing),
    saved: { executionTiming: timing },
    load: (saved: unknown) => {
      setTiming(oneOf(pickValues(DEFAULTS, saved).executionTiming, EXECUTION_TIMINGS, 'SAME_DAY_CLOSE'));
    },
  };
};
