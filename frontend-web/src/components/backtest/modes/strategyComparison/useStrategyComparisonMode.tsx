import { useState } from 'react';
import { backtestApi } from '@services/api';
import { StrategyComparisonForm } from '../../forms/StrategyComparisonForm';
import { StrategyParamsModal } from '../../modals/StrategyParamsModal';
import { StrategyComparisonResult } from '../../results/StrategyComparisonResult';
import { strategyByType } from '../../comparison/catalog';
import type { ComparisonStrategyType, StrategyParams } from '../../comparison/types';
import type { ModeController } from '../types';
import { buildStrategyComparisonRequest } from './request';
import { useBacktestOptions } from '../../shared/backtestOptions';
import { notify } from '@/components/common/notify';
import { getTodayString } from '../../../../utils/dateUtils';

// 모달에서 고치는 칩(저장 전까지는 사본, 새로 더하는 칩이면 isNew)
interface Editing {
  type: ComparisonStrategyType;
  draft: StrategyParams;
  isNew: boolean;
}

export const useStrategyComparisonMode = ({
  supportedSymbols,
}: {
  supportedSymbols: string[];
}): ModeController => {
  const [symbol, setSymbol] = useState('AAPL');
  const [startDate, setStartDate] = useState('2023-01-01');
  const [endDate, setEndDate] = useState(''); // 비어있으면 현재 날짜
  const [investment, setInvestment] = useState('1000000');
  const [selectedStrategies, setSelectedStrategies] = useState<ComparisonStrategyType[]>([
    'SIMPLE',
    'DCA',
  ]);
  const options = useBacktestOptions();

  // 칩마다 문자열로 담고 실행 직전에 숫자로 바꿈
  const [strategyParameters, setStrategyParameters] = useState<Record<string, StrategyParams>>(() => ({
    DCA: { ...strategyByType('DCA').defaultParams },
  }));
  const [editing, setEditing] = useState<Editing | null>(null);
  const common = { startDate, endDate: endDate || getTodayString(), investment };

  const openEditor = (type: ComparisonStrategyType, isNew: boolean) =>
    setEditing({ type, isNew, draft: { ...strategyByType(type).defaultParams, ...strategyParameters[type] } });

  const toggleStrategy = (strategy: ComparisonStrategyType) => {
    if (!selectedStrategies.includes(strategy)) {
      openEditor(strategy, true);
      return;
    }
    if (selectedStrategies.length > 1) {
      setSelectedStrategies(selectedStrategies.filter((s) => s !== strategy));
      const newParams = { ...strategyParameters };
      delete newParams[strategy];
      setStrategyParameters(newParams);
    }
  };

  const handleSave = () => {
    if (!editing) return;
    const message = strategyByType(editing.type).check(editing.draft, common);
    if (message) {
      notify.error(message);
      return;
    }
    setStrategyParameters({ ...strategyParameters, [editing.type]: editing.draft });
    if (editing.isNew) setSelectedStrategies([...selectedStrategies, editing.type]);
    setEditing(null);
  };

  return {
    id: 'compare-strategies',
    label: '전략 비교',
    showsChartLink: false,
    form: (
      <StrategyComparisonForm
        symbol={symbol}
        setSymbol={setSymbol}
        supportedSymbols={supportedSymbols}
        startDate={startDate}
        setStartDate={setStartDate}
        endDate={endDate}
        setEndDate={setEndDate}
        investment={investment}
        setInvestment={setInvestment}
        selectedStrategies={selectedStrategies}
        strategyParameters={strategyParameters}
        toggleStrategy={toggleStrategy}
        editStrategy={(type) => openEditor(type, false)}
        options={options}
      />
    ),
    overlay: editing ? (
      <StrategyParamsModal
        type={editing.type}
        params={editing.draft}
        onChange={(key, value) => setEditing((prev) => prev && { ...prev, draft: { ...prev.draft, [key]: value } })}
        common={common}
        onSave={handleSave}
        onCancel={() => setEditing(null)}
      />
    ) : null,
    prepare: (ctx) => {
      const built = buildStrategyComparisonRequest(
        {
          symbol,
          startDate,
          endDate,
          investment,
          selectedStrategies,
          strategyParameters,
          ...options,
        },
        ctx,
      );
      if ('error' in built) return built;
      const { request } = built;
      return {
        execute: async () => ({
          ...(await backtestApi.compareStrategies(request)).data,
          mode: 'compare-strategies' as const,
        }),
        historyType: 'COMPARISON',
        params: request,
        fxMode: options.fxMode,
        failureMessage: '전략 비교 실행에 실패했습니다.',
      };
    },
    renderResult: (result) =>
      result.mode === 'compare-strategies' ? <StrategyComparisonResult result={result} /> : null,
  };
};
