import type { MaCrossStrategyRequest } from '@services/api';
import type { BuildResult, RunContext } from '../types';
import { toOptionFields, type BacktestOptions } from '../../shared/backtestOptions';
import { parseWindows, WINDOWS_ERROR } from '../../shared/maWindows';
import { toTrailingStop } from '../../shared/trailingStop';

export interface MaCrossValues extends BacktestOptions {
  startDate: string;
  endDate: string;
  investment: string;
  shortWindow: string;
  longWindow: string;
  // 따로 고른 신호 종목(매매 종목과 같으면 비움)
  signalSymbol?: string;
  // 트레일링 스탑 고점 대비 하락 % · 보유의 팔 %(비우면 끔 · 전량)
  trailingStop?: string;
  trailingSell?: string;
}

// 이동평균 교차 요청(검사에 걸리면 alert 문구), 금액은 원금 · 두 기간은 늘 채움 · 신호 종목 · 트레일링 스탑은 적은 때만 · 배당 재투자는 늘 끔
export const buildMaCrossRequest = (
  values: MaCrossValues,
  symbol: string,
  ctx: RunContext,
): BuildResult<MaCrossStrategyRequest & { shortWindow: number; longWindow: number }> => {
  const principal = parseFloat(values.investment);
  if (!(principal > 0)) {
    return { error: '올바른 원금을 입력해주세요.' };
  }
  const windows = parseWindows(values.shortWindow, values.longWindow);
  if (!windows) {
    return { error: WINDOWS_ERROR };
  }
  const trailing = toTrailingStop(values.trailingStop ?? '', values.trailingSell ?? '');
  if ('error' in trailing) {
    return trailing;
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
      ...(values.signalSymbol && { signalSymbol: values.signalSymbol }),
      ...trailing.fields,
      ...toOptionFields(values),
      reinvestDividends: false,
    },
  };
};
