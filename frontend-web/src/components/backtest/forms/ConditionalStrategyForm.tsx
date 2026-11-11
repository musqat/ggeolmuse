import React from 'react';
import { SymbolField, type SymbolFieldProps } from '../settings/SymbolFields';
import { PeriodField, type PeriodFieldProps } from '../settings/PeriodField';
import { AmountField, type AmountFieldProps } from '../settings/AmountField';
import { BudgetField, type BudgetFieldProps } from '../settings/BudgetField';
import { ConditionField, type ConditionFieldProps } from '../settings/ConditionField';
import { SellField, type SellFieldProps } from '../settings/SellField';
import { MaxField, type MaxFieldProps } from '../settings/MaxField';
import { TrailingStopField, type TrailingStopFieldProps } from '../settings/TrailingStopField';
import { PartialTakeProfitField, type PartialTakeProfitFieldProps } from '../settings/PartialTakeProfitField';
import { FIELD_GRID } from './formLayout';

interface ConditionalStrategyFormProps {
  symbol: SymbolFieldProps;
  period: PeriodFieldProps;
  amount: AmountFieldProps;
  budget: BudgetFieldProps;
  condition: ConditionFieldProps;
  sell: SellFieldProps;
  trailing: TrailingStopFieldProps;
  max: MaxFieldProps;
  partial: PartialTakeProfitFieldProps;
}

// 정한 비율만큼 떨어지거나 오른 날마다 회당 금액 · 회당 주식 수만큼 사고, 켜면 익절 · 손절 · 트레일링 스탑 · 부분 익절로 파는 전략
export const ConditionalStrategyForm: React.FC<ConditionalStrategyFormProps> = ({
  symbol,
  period,
  amount,
  budget,
  condition,
  sell,
  trailing,
  max,
  partial,
}) => (
  <div className="space-y-4">
    <div className={FIELD_GRID}>
      <SymbolField {...symbol} />
      <PeriodField {...period} />
      <AmountField {...amount} />
      <BudgetField {...budget} />
    </div>
    <div className={FIELD_GRID}>
      <ConditionField {...condition} />
      <SellField {...sell} />
      <TrailingStopField {...trailing} />
      <MaxField {...max} />
      <PartialTakeProfitField {...partial} />
    </div>
  </div>
);
