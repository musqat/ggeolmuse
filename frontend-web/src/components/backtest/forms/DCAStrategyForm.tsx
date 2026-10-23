import React from 'react';
import { NumberInput } from '../../common/NumberInput';
import type { BacktestOptionsState } from '../shared/backtestOptions';
import {
  DcaScheduleFields,
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
  monthlyAmount: string;
  setMonthlyAmount: (amount: string) => void;
  purchaseDay: string;
  setPurchaseDay: (day: string) => void;
  investmentInterval: string;
  setInvestmentInterval: (interval: string) => void;
  options: BacktestOptionsState;
  supportedSymbols: string[];
}

// 적립식(DCA): 정한 날 정한 금액을 주기적으로 삼
export const DCAStrategyForm: React.FC<DCAStrategyFormProps> = ({
  symbol,
  setSymbol,
  dcaStartDate,
  setDcaStartDate,
  dcaEndDate,
  setDcaEndDate,
  monthlyAmount,
  setMonthlyAmount,
  purchaseDay,
  setPurchaseDay,
  investmentInterval,
  setInvestmentInterval,
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

        <FormField label="월 투자금 (₩)">
          <NumberInput
            value={monthlyAmount}
            onChange={setMonthlyAmount}
            placeholder="100,000"
            className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
          />
          <p className="text-xs text-tx-3 mt-1">매월 정기 투자 금액</p>
        </FormField>

        <DcaScheduleFields
          purchaseDay={purchaseDay}
          interval={investmentInterval}
          onPurchaseDayChange={setPurchaseDay}
          onIntervalChange={setInvestmentInterval}
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
