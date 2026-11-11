import React from 'react';
import { SymbolField, type SymbolFieldProps } from '../settings/SymbolFields';
import { PeriodField, type PeriodFieldProps } from '../settings/PeriodField';
import { AmountField, type AmountFieldProps } from '../settings/AmountField';
import { BandField, type BandFieldProps } from '../settings/BandField';
import { SignalSymbolField, type SignalSymbolFieldProps } from '../settings/SignalSymbolField';
import { TrailingStopField, type TrailingStopFieldProps } from '../settings/TrailingStopField';
import { PartialTakeProfitField, type PartialTakeProfitFieldProps } from '../settings/PartialTakeProfitField';
import { FIELD_GRID } from './formLayout';

interface MaBandStrategyFormProps {
  symbol: SymbolFieldProps;
  period: PeriodFieldProps;
  amount: AmountFieldProps;
  band: BandFieldProps;
  signal: SignalSymbolFieldProps;
  trailing: TrailingStopFieldProps;
  partial: PartialTakeProfitFieldProps;
}

// 수정 종가가 평균 위 선보다 높으면 가진 돈 전부로 사고 아래 선보다 낮으면 전부 파는 전략: 종목 · 기간 · 원금 · 밴드 · 신호 종목 · 트레일링 스탑 · 부분 익절
export const MaBandStrategyForm: React.FC<MaBandStrategyFormProps> = ({ symbol, period, amount, band, signal, trailing, partial }) => (
  <div className={FIELD_GRID}>
    <SymbolField {...symbol} />
    <PeriodField {...period} />
    <AmountField {...amount} />
    <BandField {...band} />
    <SignalSymbolField {...signal} />
    <TrailingStopField {...trailing} />
    <PartialTakeProfitField {...partial} />
  </div>
);
