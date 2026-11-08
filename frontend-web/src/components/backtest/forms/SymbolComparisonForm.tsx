import React from 'react';
import type { BacktestOptionsState } from '../shared/backtestOptions';
import { StrategyOptions } from '../shared/StrategyFields';
import { SymbolsField, type SymbolsFieldProps } from '../settings/SymbolFields';
import { PeriodField, type PeriodFieldProps } from '../settings/PeriodField';
import { AmountField, type AmountFieldProps } from '../settings/AmountField';
import { FIELD_GRID } from './formLayout';

interface SymbolComparisonFormProps {
  symbols: SymbolsFieldProps;
  period: PeriodFieldProps;
  amount: AmountFieldProps;
  options: BacktestOptionsState;
}

// 여러 종목(최대 10개)을 같은 금액으로 견줌
export const SymbolComparisonForm: React.FC<SymbolComparisonFormProps> = ({ symbols, period, amount, options }) => (
  <div className="space-y-4">
    <div className={FIELD_GRID}>
      <SymbolsField {...symbols} />
      <PeriodField {...period} />
      <AmountField {...amount} />
    </div>
    <StrategyOptions
      options={options}
      startDate={period.period.start}
      purchaseLabel="시작일 환율 (₩/USD)"
      currentLabel="현재 환율 (₩/USD)"
    />
  </div>
);
