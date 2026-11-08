import React from 'react';
import type { BacktestOptionsState } from '../shared/backtestOptions';
import type { AmountUnitState } from '../shared/amountUnit';
import { AmountOrSharesField } from '../shared/AmountOrSharesField';
import { PeriodFields, StrategyOptions, SymbolField } from '../shared/StrategyFields';

interface SimpleStrategyFormProps {
  symbol: string;
  setSymbol: (symbol: string) => void;
  purchaseDate: string;
  setPurchaseDate: (date: string) => void;
  saleDate: string;
  setSaleDate: (date: string) => void;
  initialInvestment: string;
  setInitialInvestment: (amount: string) => void;
  amountUnit: AmountUnitState;
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
  amountUnit,
  options,
  supportedSymbols,
}) => {
  return (
    <div className="space-y-4">
      {/* 기본 설정 - 모바일 완전 세로 배치 */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3">
        <SymbolField value={symbol} onChange={setSymbol} supportedSymbols={supportedSymbols} />

        <AmountOrSharesField
          amountLabel="초기 투자금 (₩)"
          sharesLabel="주식 수"
          amount={initialInvestment}
          onAmountChange={setInitialInvestment}
          amountPlaceholder="300,000"
          amountHelp="최소 약 30만원 권장"
          amountUnit={amountUnit}
          symbol={symbol}
          startDate={purchaseDate}
          options={options}
        />

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
