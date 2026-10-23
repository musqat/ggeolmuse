import React from 'react';
import { NumberInput } from '../../common/NumberInput';
import type { BacktestOptionsState } from '../shared/backtestOptions';
import { COMPARISON_STRATEGIES } from '../comparison/catalog';
import type { ComparisonStrategyType } from '../comparison/types';
import { FormField, PeriodFields, StrategyOptions, SymbolField } from '../shared/StrategyFields';

interface StrategyComparisonFormProps {
  symbol: string;
  setSymbol: (symbol: string) => void;
  supportedSymbols: string[];

  startDate: string;
  setStartDate: (date: string) => void;
  endDate: string;
  setEndDate: (date: string) => void;

  investment: string;
  setInvestment: (amount: string) => void;

  selectedStrategies: string[];
  toggleStrategy: (strategy: ComparisonStrategyType) => void;

  options: BacktestOptionsState;
}

// 한 종목에 단순 · 적립식 · 조건부 매수를 견줌
export const StrategyComparisonForm: React.FC<StrategyComparisonFormProps> = ({
  symbol,
  setSymbol,
  supportedSymbols,
  startDate,
  setStartDate,
  endDate,
  setEndDate,
  investment,
  setInvestment,
  selectedStrategies,
  toggleStrategy,
  options,
}) => {
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <SymbolField value={symbol} onChange={setSymbol} supportedSymbols={supportedSymbols} />

        <PeriodFields
          start={startDate}
          end={endDate}
          onStartChange={setStartDate}
          onEndChange={setEndDate}
        />

        <FormField label="투자금 (₩)">
          <NumberInput
            value={investment}
            onChange={setInvestment}
            placeholder="1,000,000"
            className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
          />
        </FormField>
      </div>

      <FormField label="비교할 전략 선택 (최소 2개)">
        <div className="flex flex-wrap gap-2">
          {COMPARISON_STRATEGIES.map(({ type, name }) => (
            <button
              key={type}
              onClick={() => toggleStrategy(type)}
              className={`px-4 py-2 rounded-md transition-colors ${
                selectedStrategies.includes(type)
                  ? 'bg-brand text-brand-ink'
                  : 'bg-elevated/50 text-tx-2 hover:bg-hover hover:text-tx-1 border border-line'
              }`}
            >
              {name}
            </button>
          ))}
        </div>
      </FormField>

      <StrategyOptions
        options={options}
        startDate={startDate}
        purchaseLabel="시작일 환율 (₩/USD)"
        currentLabel="현재 환율 (₩/USD)"
      />
    </div>
  );
};
