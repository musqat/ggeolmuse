import React from 'react';
import { SymbolField, type SymbolFieldProps } from '../settings/SymbolFields';
import { PeriodField, type PeriodFieldProps } from '../settings/PeriodField';
import { AmountField, type AmountFieldProps } from '../settings/AmountField';
import { WindowsField, type WindowsFieldProps } from '../settings/WindowsField';
import { SignalSymbolField, type SignalSymbolFieldProps } from '../settings/SignalSymbolField';
import { TrailingStopField, type TrailingStopFieldProps } from '../settings/TrailingStopField';
import { FIELD_GRID } from './formLayout';

interface MaCrossStrategyFormProps {
  symbol: SymbolFieldProps;
  period: PeriodFieldProps;
  amount: AmountFieldProps;
  windows: WindowsFieldProps;
  signal: SignalSymbolFieldProps;
  trailing: TrailingStopFieldProps;
}

// 짧은 이동평균이 긴 이동평균을 위로 넘으면 가진 돈 전부로 사고 아래로 넘으면 전부 파는 전략: 종목 · 기간 · 원금 · 두 기간 · 신호 종목 · 트레일링 스탑
export const MaCrossStrategyForm: React.FC<MaCrossStrategyFormProps> = ({ symbol, period, amount, windows, signal, trailing }) => (
  <div className={FIELD_GRID}>
    <SymbolField {...symbol} />
    <PeriodField {...period} />
    <AmountField {...amount} />
    <WindowsField {...windows} />
    <SignalSymbolField {...signal} />
    <TrailingStopField {...trailing} />
  </div>
);
