import React from 'react';
import StockSearchInput from '../../common/StockSearchInput';
import { NumberInput } from '../../common/NumberInput';
import { FxModeToggle } from '../shared/FxModeToggle';
import { DividendFeeOptions } from '../shared/DividendFeeOptions';
import { FxDataWarning } from '../shared/FxDataWarning';
import type { BacktestOptionsState } from '../shared/backtestOptions';
import { DateField } from '../../common/DateField';
import { COMPARISON_STRATEGIES } from '../comparison/catalog';
import type { ComparisonStrategyType } from '../comparison/types';
import { getTodayString } from '../../../utils/dateUtils';

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
  const today = getTodayString();

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <div>
          <label className="block text-sm font-medium text-tx-1 mb-2">종목</label>
          <StockSearchInput
            value={symbol}
            onChange={setSymbol}
            supportedSymbols={supportedSymbols}
            placeholder="종목 검색"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-tx-1 mb-2">시작일</label>
          <DateField
            value={startDate}
            onChange={setStartDate}
            max={endDate || today}
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-tx-1 mb-2">종료일</label>
          <DateField
            value={endDate || today}
            onChange={setEndDate}
            min={startDate}
            max={today}
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-tx-1 mb-2">투자금 (₩)</label>
          <NumberInput
            value={investment}
            onChange={setInvestment}
            placeholder="1,000,000"
            className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
          />
        </div>
      </div>

      <div>
        <label className="block text-sm font-medium text-tx-1 mb-2">비교할 전략 선택 (최소 2개)</label>
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
      </div>

      {/* 환율 데이터 부족 경고 */}
      <FxDataWarning startDate={startDate} />

      {/* FX Mode Toggle */}
      <FxModeToggle
        options={options}
        purchaseLabel="시작일 환율 (₩/USD)"
        currentLabel="현재 환율 (₩/USD)"
      />

      {/* Dividend and Fee Options */}
      <DividendFeeOptions
        options={options}
      />
    </div>
  );
};
