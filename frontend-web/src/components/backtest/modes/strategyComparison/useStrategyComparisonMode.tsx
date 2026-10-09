import { useState } from 'react';
import { backtestApi } from '@services/api';
import { StrategyComparisonForm } from '../../forms/StrategyComparisonForm';
import { StrategyParamsModal } from '../../modals/StrategyParamsModal';
import { StrategyComparisonResult } from '../../results/StrategyComparisonResult';
import { strategyByType } from '../../comparison/catalog';
import type { ComparisonStrategyType, StrategyParams } from '../../comparison/types';
import type { ModeController } from '../types';
import { buildStrategyComparisonRequest } from './request';

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
  const [reinvestDividends, setReinvestDividends] = useState(false);
  const [tradingFeeRate, setTradingFeeRate] = useState('0');
  const [dividendTax, setDividendTax] = useState(false);
  const [fxMode, setFxMode] = useState<'auto' | 'manual'>('auto');
  const [manualPurchaseFxRate, setManualPurchaseFxRate] = useState('1300');
  const [manualCurrentFxRate, setManualCurrentFxRate] = useState('1350');

  // 전략 파라미터 모달
  const [showStrategyModal, setShowStrategyModal] = useState(false);
  const [modalStrategyType, setModalStrategyType] = useState<ComparisonStrategyType | null>(null);
  // 폼이 입력값을 문자열로 담고, 실행 직전에 숫자로 바꾼다.
  const [strategyParameters, setStrategyParameters] = useState<Record<string, StrategyParams>>({});

  const toggleStrategy = (strategy: ComparisonStrategyType) => {
    if (selectedStrategies.includes(strategy)) {
      if (selectedStrategies.length > 1) {
        setSelectedStrategies(selectedStrategies.filter((s) => s !== strategy));
        const newParams = { ...strategyParameters };
        delete newParams[strategy];
        setStrategyParameters(newParams);
      }
    } else {
      setModalStrategyType(strategy);

      // 기본값 설정
      if (!strategyParameters[strategy]) {
        setStrategyParameters({
          ...strategyParameters,
          [strategy]: { ...strategyByType(strategy).defaultParams },
        });
      }

      setShowStrategyModal(true);
    }
  };

  const handleSaveStrategyParams = () => {
    if (!modalStrategyType) return;

    const message = strategyByType(modalStrategyType).checkOnSave(
      strategyParameters[modalStrategyType] || {},
    );
    if (message) {
      alert(message);
      return;
    }

    setSelectedStrategies([...selectedStrategies, modalStrategyType]);
    setShowStrategyModal(false);
    setModalStrategyType(null);
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
        toggleStrategy={toggleStrategy}
        fxMode={fxMode}
        setFxMode={setFxMode}
        manualPurchaseFxRate={manualPurchaseFxRate}
        setManualPurchaseFxRate={setManualPurchaseFxRate}
        manualCurrentFxRate={manualCurrentFxRate}
        setManualCurrentFxRate={setManualCurrentFxRate}
        tradingFeeRate={tradingFeeRate}
        setTradingFeeRate={setTradingFeeRate}
        dividendTax={dividendTax}
        setDividendTax={setDividendTax}
        reinvestDividends={reinvestDividends}
        setReinvestDividends={setReinvestDividends}
      />
    ),
    overlay:
      showStrategyModal && modalStrategyType ? (
        <StrategyParamsModal
          modalStrategyType={modalStrategyType}
          strategyParameters={strategyParameters}
          setStrategyParameters={setStrategyParameters}
          strategyStartDate={startDate}
          strategyInvestment={investment}
          handleSaveStrategyParams={handleSaveStrategyParams}
          setShowStrategyModal={setShowStrategyModal}
          setModalStrategyType={setModalStrategyType}
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
          reinvestDividends,
          tradingFeeRate,
          dividendTax,
          fxMode,
          manualPurchaseFxRate,
          manualCurrentFxRate,
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
        fxMode,
        failureMessage: '전략 비교 실행에 실패했습니다.',
      };
    },
    renderResult: (result) =>
      result.mode === 'compare-strategies' ? <StrategyComparisonResult result={result} /> : null,
  };
};
