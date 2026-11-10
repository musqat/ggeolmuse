import type { MaCrossStrategyRequest } from '@services/api';
import type { BuildResult, RunContext } from '../types';
import { toOptionFields, type BacktestOptions } from '../../shared/backtestOptions';
import { parseWindows, WINDOWS_ERROR } from '../../shared/maWindows';

export interface MaCrossValues extends BacktestOptions {
  startDate: string;
  endDate: string;
  investment: string;
  shortWindow: string;
  longWindow: string;
}

// 이동평균 교차 요청(검사에 걸리면 alert 문구), 금액은 원금 · 배당 재투자는 늘 끔
export const buildMaCrossRequest = (
  values: MaCrossValues,
  symbol: string,
  ctx: RunContext,
): BuildResult<MaCrossStrategyRequest> => {
  const principal = parseFloat(values.investment);
  if (!(principal > 0)) {
    return { error: '올바른 원금을 입력해주세요.' };
  }
  const windows = parseWindows(values.shortWindow, values.longWindow);
  if (!windows) {
    return { error: WINDOWS_ERROR };
  }

  // 빈 종료일은 오늘로 채워 검사
  const effectiveEndDate = values.endDate || ctx.today;
  if (values.startDate >= effectiveEndDate) {
    return { error: '시작일은 종료일보다 빠른 날짜여야 합니다.' };
  }

  return {
    request: {
      symbol,
      startDate: values.startDate,
      endDate: effectiveEndDate,
      investmentAmount: principal,
      ...windows,
      ...toOptionFields(values),
      reinvestDividends: false,
    },
  };
};
