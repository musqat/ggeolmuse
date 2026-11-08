import React from 'react';
import type { BacktestOptionsState } from '../shared/backtestOptions';
import { StrategyOptions } from '../shared/StrategyFields';
import { SymbolField, type SymbolFieldProps } from '../settings/SymbolFields';
import { PeriodField, type PeriodFieldProps } from '../settings/PeriodField';
import { AmountField, type AmountFieldProps } from '../settings/AmountField';
import { FIELD_GRID } from './formLayout';

interface SimpleStrategyFormProps {
  symbol: SymbolFieldProps;
  period: PeriodFieldProps;
  amount: AmountFieldProps;
  options: BacktestOptionsState;
}

// 한 날 사서 들고 있는 경우: 종목 · 기간 · 금액(또는 주식 수)
export const SimpleStrategyForm: React.FC<SimpleStrategyFormProps> = ({ symbol, period, amount, options }) => (
  <div className="space-y-4">
    <div className={FIELD_GRID}>
      <SymbolField {...symbol} />
      <PeriodField {...period} />
      <AmountField {...amount} />
    </div>
    <StrategyOptions options={options} startDate={period.period.start} purchaseLabel="매수일 환율" currentLabel="매도일 환율" />
  </div>
);
