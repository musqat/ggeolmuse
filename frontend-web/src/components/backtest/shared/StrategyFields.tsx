import React from 'react';
import { FxModeToggle } from './FxModeToggle';
import { DividendFeeOptions } from './DividendFeeOptions';
import { FxDataWarning } from './FxDataWarning';
import type { BacktestOptionsState } from './backtestOptions';

interface StrategyOptionsProps {
  options: BacktestOptionsState;
  startDate: string;
  purchaseLabel: string;
  currentLabel: string;
  // 익절 · 손절을 켜서 배당 재투자를 못 쓰는지
  reinvestLocked?: boolean;
  // 배당 재투자 칸 옆 안내를 바꿀 문구
  reinvestNote?: string;
}

// 환율 데이터 경고 · 환율 설정 · 배당 · 수수료 옵션
export const StrategyOptions: React.FC<StrategyOptionsProps> = ({
  options,
  startDate,
  purchaseLabel,
  currentLabel,
  reinvestLocked = false,
  reinvestNote,
}) => (
  <>
    <FxDataWarning startDate={startDate} />
    <FxModeToggle options={options} purchaseLabel={purchaseLabel} currentLabel={currentLabel} />
    <DividendFeeOptions options={options} reinvestLocked={reinvestLocked} reinvestNote={reinvestNote} />
  </>
);
