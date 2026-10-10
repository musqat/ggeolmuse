import type { ReactNode } from 'react';
import type { BacktestHistoryDto } from '@services/api';
import type { BacktestMode, BacktestResult } from '../shared/backtestDisplay';

// 실행 모드. history 는 실행이 없어 뺀다
export type RunMode = Exclude<BacktestMode, 'history'>;

// 실행 순간에 정하는 값
export interface RunContext {
  userId: string;
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

// 단순 · 적립식 · 조건부가 같이 쓰는 종목
export interface SharedSymbolProps {
  symbol: string;
  setSymbol: (symbol: string) => void;
  supportedSymbols: string[];
}

export interface ModeController {
  id: RunMode;
  // 탭 이름
  label: string;
  // 상세 차트 보기 링크
  showsChartLink: boolean;
  form: ReactNode;
  // 모드가 띄우는 모달
  overlay?: ReactNode;
  prepare: (ctx: RunContext) => Prepared;
  // 다른 모드 결과면 null
  renderResult: (result: BacktestResult) => ReactNode;
}
