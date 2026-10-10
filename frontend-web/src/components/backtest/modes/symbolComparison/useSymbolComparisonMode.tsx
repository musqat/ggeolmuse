import { useState } from 'react';
import { backtestApi } from '@services/api';
import { SymbolComparisonForm } from '../../forms/SymbolComparisonForm';
import { SymbolComparisonResult } from '../../results/SymbolComparisonResult';
import type { OptimalPointsBySymbol } from '../../shared/backtestDisplay';
import type { ModeController } from '../types';
import { buildSymbolComparisonRequest } from './request';
import { useBacktestOptions } from '../../shared/backtestOptions';

export const useSymbolComparisonMode = ({
  supportedSymbols,
}: {
  supportedSymbols: string[];
}): ModeController => {
  const [symbols, setSymbols] = useState<string[]>(['AAPL', 'MSFT']);
  const [symbolInput, setSymbolInput] = useState('');
  const [purchaseDate, setPurchaseDate] = useState('2023-01-01');
  const [saleDate, setSaleDate] = useState(''); // 비어있으면 최신 데이터
  const [investment, setInvestment] = useState('1000000');
  const options = useBacktestOptions();
  const [symbolOptimalPoints, setSymbolOptimalPoints] = useState<OptimalPointsBySymbol>({});

  const addSymbol = () => {
    if (symbols.length >= 10) {
      alert('최대 10개까지만 비교할 수 있습니다.');
      return;
    }
    if (symbolInput && !symbols.includes(symbolInput)) {
      setSymbols([...symbols, symbolInput]);
      setSymbolInput('');
    }
  };

  const removeSymbol = (symbolToRemove: string) => {
    setSymbols(symbols.filter((s) => s !== symbolToRemove));
  };

  return {
    id: 'compare-symbols',
    label: '종목 비교',
    showsChartLink: false,
    form: (
      <SymbolComparisonForm
        compareSymbols={symbols}
        setCompareSymbols={setSymbols}
        compareSymbolInput={symbolInput}
        setCompareSymbolInput={setSymbolInput}
        comparePurchaseDate={purchaseDate}
        setComparePurchaseDate={setPurchaseDate}
        compareSaleDate={saleDate}
        setCompareSaleDate={setSaleDate}
        compareInvestment={investment}
        setCompareInvestment={setInvestment}
        options={options}
        supportedSymbols={supportedSymbols}
        onAddSymbol={addSymbol}
        onRemoveSymbol={removeSymbol}
      />
    ),
    prepare: (ctx) => {
      const built = buildSymbolComparisonRequest(
        {
          symbols,
          purchaseDate,
          saleDate,
          investment,
          ...options,
        },
        ctx,
      );
      if ('error' in built) return built;
      const { request } = built;
      return {
        execute: async () => ({
          ...(await backtestApi.compareSymbols(request)).data,
          mode: 'compare-symbols' as const,
        }),
        historyType: 'COMPARISON',
        params: request,
        fxMode: options.fxMode,
        failureMessage: '종목 비교 실행에 실패했습니다.',
      };
    },
    renderResult: (result) =>
      result.mode === 'compare-symbols' ? (
        <SymbolComparisonResult
          result={result}
          comparePurchaseDate={purchaseDate}
          compareSaleDate={saleDate}
          symbolOptimalPoints={symbolOptimalPoints}
          setSymbolOptimalPoints={setSymbolOptimalPoints}
        />
      ) : null,
  };
};
