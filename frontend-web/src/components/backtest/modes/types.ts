import type { ReactNode } from 'react';
import type { BacktestHistoryDto } from '@services/api';
import type { BacktestMode, BacktestResult } from '../shared/backtestDisplay';
import type { PeriodState } from '../settings/period';
import type { BacktestOptionsState } from '../shared/backtestOptions';
import type { ModeSummary } from '../settings/SettingsSummary';

export type RunMode = BacktestMode;

// 실행 순간에 정하는 값
export interface RunContext {
  // 빈 종료일 · 매도일을 채우고 단순 매수일이 과거인지 본다 (YYYY-MM-DD, 로컬 날짜)
  today: string;
}

// 검사를 통과한 요청 또는 alert 문구
export type BuildResult<T> = { request: T } | { error: string };

export interface PreparedRun {
  // API 호출 + mode 태그
  execute: () => Promise<BacktestResult>;
  historyType: BacktestHistoryDto['backtestType'];
  // 비로그인 로컬 히스토리에 저장할 요청
  params: object;
  fxMode: 'auto' | 'manual';
  failureMessage: string;
}

export type Prepared = PreparedRun | { error: string };

// 모드가 같이 쓰는 종목(종목 비교는 담을 때만) · 기간 · 세부 설정
export interface SharedModeProps {
  symbol: string;
  setSymbol: (symbol: string) => void;
  supportedSymbols: string[];
  period: PeriodState;
  options: BacktestOptionsState;
  // 세부 설정을 열고 환율 직접 입력의 매수 환율 칸으로
  openManualFx: () => void;
}

export interface ModeController {
  id: RunMode;
  // 모드 고르기 이름
  label: string;
  // 상세 차트 보기 링크
  showsChartLink: boolean;
  form: ReactNode;
  summary: ModeSummary;
  // 실행을 막는 칸 오류 수
  errorCount: number;
  // 익절 · 손절이 있어 세부 설정의 배당 재투자를 못 쓰는지
  reinvestLocked?: boolean;
  // 재투자를 막지 않는 배당 칸 안내
  reinvestNote?: string;
  // 직전 설정 · 설정 초기화가 저장하는 이 모드의 값
  saved: object;
  // 저장한 값(없거나 모양이 다른 칸은 기본값)
  load: (saved: unknown) => void;
  // 이 모드로 바꿀 때
  onEnter?: () => void;
  prepare: (ctx: RunContext) => Prepared;
  // 다른 모드 결과면 null
  renderResult: (result: BacktestResult) => ReactNode;
}
