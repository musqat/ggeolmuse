import React from 'react';
import { NumberInput } from '../../common/NumberInput';
import type { BacktestOptionsState } from '../shared/backtestOptions';
import { FormField, PeriodFields, StrategyOptions, SymbolField } from '../shared/StrategyFields';

interface SimpleStrategyFormProps {
  symbol: string;
  setSymbol: (symbol: string) => void;
  purchaseDate: string;
  setPurchaseDate: (date: string) => void;
  saleDate: string;
  setSaleDate: (date: string) => void;
  initialInvestment: string;
  setInitialInvestment: (amount: string) => void;
  options: BacktestOptionsState;
  supportedSymbols: string[];
}

// 한 날 사서 들고 있는 경우
export const SimpleStrategyForm: React.FC<SimpleStrategyFormProps> = ({
  symbol,
  setSymbol,
  purchaseDate,
  setPurchaseDate,
  saleDate,
  setSaleDate,
  initialInvestment,
  setInitialInvestment,
  options,
  supportedSymbols,
}) => {
  return (
    <div className="space-y-4">
      {/* 기본 설정 - 모바일 완전 세로 배치 */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3">
        <SymbolField value={symbol} onChange={setSymbol} supportedSymbols={supportedSymbols} />

        <FormField label="초기 투자금 (₩)">
          <NumberInput
            value={initialInvestment}
            onChange={setInitialInvestment}
            placeholder="300,000"
            className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
          />
          <p className="text-xs text-tx-3 mt-1">최소 약 30만원 권장</p>
        </FormField>

        <PeriodFields
          start={purchaseDate}
          end={saleDate}
          onStartChange={setPurchaseDate}
          onEndChange={setSaleDate}
        />
      </div>

      <StrategyOptions
        options={options}
        startDate={purchaseDate}
        purchaseLabel="매수일 환율"
        currentLabel="매도일 환율"
      />
    </div>
  );
};
