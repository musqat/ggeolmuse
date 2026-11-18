import { useRef, useState } from 'react';
import { saveLocalBacktestHistory } from '@utils/localBacktestHistory';
import { getApiErrorMessage } from '@utils/apiError';
import type { BacktestResult } from '../shared/backtestDisplay';
import type { PreparedRun, RunInputs } from './types';

// 실행 상태 · 결과와 그 실행의 종목 · 기간 · 오류(비로그인이면 성공한 요청을 로컬 히스토리에 저장)
export const useBacktestRunner = (isAuthenticated: boolean) => {
  const [isRunning, setIsRunning] = useState(false);
  const [outcome, setOutcome] = useState<{ result: BacktestResult; inputs: RunInputs } | null>(null);
  const [error, setError] = useState<string | null>(null);
  // 마지막 실행 번호(다시 실행하거나 지우면 앞 실행의 응답 · 오류는 버림)
  const latest = useRef(0);

  const run = async (prepared: PreparedRun, inputs: RunInputs) => {
    const id = ++latest.current;
    setIsRunning(true);
    setError(null);
    setOutcome(null);
    try {
      const next = await prepared.execute();
      if (!isAuthenticated) {
        saveLocalBacktestHistory(prepared.historyType, prepared.params, prepared.fxMode);
      }
      if (id === latest.current) setOutcome({ result: next, inputs });
    } catch (err: unknown) {
      if (id === latest.current) setError(getApiErrorMessage(err, prepared.failureMessage));
    } finally {
      if (id === latest.current) setIsRunning(false);
    }
  };

  const reset = () => {
    latest.current += 1;
    setIsRunning(false);
    setOutcome(null);
    setError(null);
  };

  return { isRunning, result: outcome?.result ?? null, inputs: outcome?.inputs ?? null, error, run, reset };
};
