import { useState } from 'react';
import { saveLocalBacktestHistory } from '@utils/localBacktestHistory';
import { getApiErrorMessage } from '@utils/apiError';
import type { BacktestResult } from '../shared/backtestDisplay';
import type { PreparedRun } from './types';

// 실행 상태 · 결과 · 오류. 비로그인이면 성공한 요청을 로컬 히스토리에 저장한다
export const useBacktestRunner = (isAuthenticated: boolean) => {
  const [isRunning, setIsRunning] = useState(false);
  const [result, setResult] = useState<BacktestResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const run = async (prepared: PreparedRun) => {
    setIsRunning(true);
    setError(null);
    setResult(null);
    try {
      const next = await prepared.execute();
      if (!isAuthenticated) {
        saveLocalBacktestHistory(prepared.historyType, prepared.params, prepared.fxMode);
      }
      // TODO: 로그인 후 sync API 생기면 여기서 처리
      setResult(next);
    } catch (err: unknown) {
      setError(getApiErrorMessage(err, prepared.failureMessage));
    } finally {
      setIsRunning(false);
    }
  };

  const reset = () => {
    setResult(null);
    setError(null);
  };

  return { isRunning, result, error, run, reset };
};
