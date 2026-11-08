import React from 'react';
import { NumberInput } from '../../common/NumberInput';
import type { BacktestOptionsState } from '../shared/backtestOptions';
import { COMPARISON_STRATEGIES, strategyByType } from '../comparison/catalog';
import type { ComparisonCommon, ComparisonStrategyType, StrategyParams } from '../comparison/types';
import { FormField, PeriodFields, StrategyOptions, SymbolField } from '../shared/StrategyFields';
import { hasSellRates } from '../shared/sellRates';
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

  selectedStrategies: ComparisonStrategyType[];
  strategyParameters: Record<string, StrategyParams>;
  toggleStrategy: (strategy: ComparisonStrategyType) => void;
  editStrategy: (strategy: ComparisonStrategyType) => void;

  options: BacktestOptionsState;
}

// 고른 칩 한 줄: 검사에 걸리면 그 오류, 아니면 요약(원금이 모자라면 경고 색)
const ChipLine: React.FC<{ type: ComparisonStrategyType; params: StrategyParams; common: ComparisonCommon }> = ({
  type,
  params,
  common,
}) => {
  const def = strategyByType(type);
  const error = def.check(params, common);
  if (error) return <span className="text-danger">{error}</span>;
  const { text, short } = def.summary(params, common);
  return <span className={short ? 'text-warning' : 'text-tx-2'}>{`${def.name}: ${text}`}</span>;
};

// 한 종목에 단순 · 적립식 · 조건부 매수를 견줌(금액은 전략마다 원금, 회당 금액 · 주기 · 조건은 칩마다)
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
  strategyParameters,
  toggleStrategy,
  editStrategy,
  options,
}) => {
  const common = { startDate, endDate: endDate || getTodayString(), investment };
  const conditional = strategyParameters.CONDITIONAL_PURCHASE;
  const conditionalSells =
    selectedStrategies.includes('CONDITIONAL_PURCHASE') && hasSellRates(conditional?.takeProfit, conditional?.stopLoss);

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

        <FormField label="전략마다 원금 (₩)">
          <NumberInput
            value={investment}
            onChange={setInvestment}
            placeholder="1,000,000"
            className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
          />
        </FormField>
      </div>

      <FormField label="비교할 전략 선택 (최소 2개)">
        <div role="group" aria-label="비교할 전략" className="flex flex-wrap gap-2">
          {COMPARISON_STRATEGIES.map(({ type, name }) => (
            <button
              key={type}
              type="button"
              aria-pressed={selectedStrategies.includes(type)}
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
        <ul className="mt-3 space-y-1.5 text-sm">
          {selectedStrategies.map((type) => (
            <li key={type} className="flex items-start justify-between gap-3">
              <ChipLine type={type} params={strategyParameters[type] ?? {}} common={common} />
              {type !== 'SIMPLE' && (
                <button
                  type="button"
                  aria-label={`${strategyByType(type).name} 설정`}
                  onClick={() => editStrategy(type)}
                  className="shrink-0 text-xs text-brand hover:underline whitespace-nowrap"
                >
                  설정
                </button>
              )}
            </li>
          ))}
        </ul>
      </FormField>

      <StrategyOptions
        options={options}
        startDate={startDate}
        purchaseLabel="시작일 환율 (₩/USD)"
        currentLabel="현재 환율 (₩/USD)"
        reinvestNote={conditionalSells ? '익절 · 손절이 있는 조건부 매수는 재투자 안 함' : undefined}
      />
    </div>
  );
};
