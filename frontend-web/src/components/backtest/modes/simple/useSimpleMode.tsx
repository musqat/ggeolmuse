import { useState } from 'react';
import { backtestApi } from '@services/api';
import { SimpleStrategyForm } from '../../forms/SimpleStrategyForm';
import { SimpleResult } from '../../results/SimpleResult';
import type { ModeController, SharedSymbolProps } from '../types';
import { buildSimpleRequest } from './request';
import { useBacktestOptions } from '../../shared/backtestOptions';

export const useSimpleMode = ({
  symbol,
  setSymbol,
  supportedSymbols,
}: SharedSymbolProps): ModeController => {
  const [purchaseDate, setPurchaseDate] = useState('2023-01-01');
  const [saleDate, setSaleDate] = useState(''); // 비어있으면 현재 날짜
  const [initialInvestment, setInitialInvestment] = useState('300000');
  const options = useBacktestOptions();

  return {
    id: 'simple',
    label: '단순',
    showsChartLink: true,
    form: (
      <SimpleStrategyForm
        symbol={symbol}
        setSymbol={setSymbol}
        purchaseDate={purchaseDate}
        setPurchaseDate={setPurchaseDate}
        saleDate={saleDate}
        setSaleDate={setSaleDate}
        initialInvestment={initialInvestment}
        setInitialInvestment={setInitialInvestment}
        options={options}
        supportedSymbols={supportedSymbols}
      />
    ),
    prepare: (ctx) => {
      const built = buildSimpleRequest(
        {
          purchaseDate,
          saleDate,
          initialInvestment,
          ...options,
        },
        symbol,
        ctx,
      );
      if ('error' in built) return built;
      const { request } = built;
      return {
        execute: async () => ({
          ...(await backtestApi.runSimulation(request)).data,
          mode: 'simple' as const,
        }),
        historyType: 'STRATEGY_SIMULATION',
        params: request,
        fxMode: options.fxMode,
        failureMessage: '백테스트 실행에 실패했습니다.',
      };
    },
    renderResult: (result) =>
      result.mode === 'simple' ? (
        <SimpleResult result={result} symbol={symbol} purchaseDate={purchaseDate} />
      ) : null,
  };
};
