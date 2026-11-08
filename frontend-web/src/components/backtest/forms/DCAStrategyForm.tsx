import React from 'react';
import type { BacktestOptionsState } from '../shared/backtestOptions';
import { StrategyOptions } from '../shared/StrategyFields';
import { SymbolField, type SymbolFieldProps } from '../settings/SymbolFields';
import { PeriodField, type PeriodFieldProps } from '../settings/PeriodField';
import { AmountField, type AmountFieldProps } from '../settings/AmountField';
import { BudgetField, type BudgetFieldProps } from '../settings/BudgetField';
import { CadenceField, type CadenceFieldProps } from '../settings/CadenceField';
import { FirstDateField, type FirstDateFieldProps } from '../settings/FirstDateField';
import { FIELD_GRID } from './formLayout';

interface DCAStrategyFormProps {
  symbol: SymbolFieldProps;
  period: PeriodFieldProps;
  amount: AmountFieldProps;
  // 주식 수 단위일 때만
  budget?: BudgetFieldProps;
  cadence: CadenceFieldProps;
  first: FirstDateFieldProps;
  options: BacktestOptionsState;
}

// 적립식(DCA): 첫 매수일부터 주기마다 회당 금액 · 회당 주식 수만큼 삼
export const DCAStrategyForm: React.FC<DCAStrategyFormProps> = ({ symbol, period, amount, budget, cadence, first, options }) => (
  <div className="space-y-4">
    <div className={FIELD_GRID}>
      <SymbolField {...symbol} />
      <PeriodField {...period} />
      <AmountField {...amount} />
      {budget && <BudgetField {...budget} />}
    </div>
    <div className={FIELD_GRID}>
      <CadenceField {...cadence} />
      <FirstDateField {...first} />
    </div>
    <StrategyOptions options={options} startDate={period.period.start} purchaseLabel="시작일 환율" currentLabel="현재 환율" />
  </div>
);
