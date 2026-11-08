import React from 'react';
import { SymbolsField, type SymbolsFieldProps } from '../settings/SymbolFields';
import { PeriodField, type PeriodFieldProps } from '../settings/PeriodField';
import { AmountField, type AmountFieldProps } from '../settings/AmountField';
import { FIELD_GRID } from './formLayout';

interface SymbolComparisonFormProps {
  symbols: SymbolsFieldProps;
  period: PeriodFieldProps;
  amount: AmountFieldProps;
}

// 여러 종목(최대 10개)을 같은 금액으로 견줌
export const SymbolComparisonForm: React.FC<SymbolComparisonFormProps> = ({ symbols, period, amount }) => (
  <div className={FIELD_GRID}>
    <SymbolsField {...symbols} />
    <PeriodField {...period} />
    <AmountField {...amount} />
  </div>
);
