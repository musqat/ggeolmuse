import React from 'react';
import { NumberInput } from '../../common/NumberInput';
import type { BacktestOptionsState } from '../shared/backtestOptions';
import type { DcaCadence } from '../shared/dcaSchedule';
import {
  DcaCadenceFields,
  FormField,
  PeriodFields,
  StrategyOptions,
  SymbolField,
} from '../shared/StrategyFields';

interface DCAStrategyFormProps {
  symbol: string;
  setSymbol: (symbol: string) => void;
  dcaStartDate: string;
  setDcaStartDate: (date: string) => void;
  dcaEndDate: string;
  setDcaEndDate: (date: string) => void;
  amountPerPurchase: string;
  setAmountPerPurchase: (amount: string) => void;
  cadence: DcaCadence;
  setCadence: (cadence: DcaCadence) => void;
  months: string;
  setMonths: (months: string) => void;
  firstPurchaseDate: string;
  setFirstPurchaseDate: (date: string) => void;
  options: BacktestOptionsState;
  supportedSymbols: string[];
}

// 적립식(DCA): 첫 매수일부터 주기마다 회당 금액만큼 삼
export const DCAStrategyForm: React.FC<DCAStrategyFormProps> = ({
  symbol,
  setSymbol,
  dcaStartDate,
  setDcaStartDate,
  dcaEndDate,
  setDcaEndDate,
  amountPerPurchase,
  setAmountPerPurchase,
  cadence,
  setCadence,
  months,
  setMonths,
  firstPurchaseDate,
  setFirstPurchaseDate,
  options,
  supportedSymbols,
}) => {
  return (
    <div className="space-y-4">
      {/* 기본 설정 */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-4">
        <SymbolField value={symbol} onChange={setSymbol} supportedSymbols={supportedSymbols} />

        <PeriodFields
          start={dcaStartDate}
          end={dcaEndDate}
          onStartChange={setDcaStartDate}
          onEndChange={setDcaEndDate}
        />

        <FormField label="회당 금액 (₩)">
          <NumberInput
            value={amountPerPurchase}
            onChange={setAmountPerPurchase}
            placeholder="100,000"
            className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
          />
          <p className="text-xs text-tx-3 mt-1">회차마다 넣는 금액</p>
        </FormField>

        <DcaCadenceFields
          cadence={cadence}
          onCadenceChange={setCadence}
          months={months}
          onMonthsChange={setMonths}
          firstPurchaseDate={firstPurchaseDate}
          onFirstPurchaseDateChange={setFirstPurchaseDate}
          startDate={dcaStartDate}
          endDate={dcaEndDate}
        />
      </div>

      <StrategyOptions
        options={options}
        startDate={dcaStartDate}
        purchaseLabel="시작일 환율"
        currentLabel="현재 환율"
      />
    </div>
  );
};
