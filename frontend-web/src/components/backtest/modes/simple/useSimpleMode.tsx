import { useState } from 'react';
import { backtestApi } from '@services/api';
import { SimpleStrategyForm } from '../../forms/SimpleStrategyForm';
import { SimpleResult } from '../../results/SimpleResult';
import type { ModeController, SharedSymbolProps } from '../types';
import { buildSimpleRequest } from './request';

export const useSimpleMode = ({
  symbol,
  setSymbol,
  supportedSymbols,
}: SharedSymbolProps): ModeController => {
  const [purchaseDate, setPurchaseDate] = useState('2023-01-01');
  const [saleDate, setSaleDate] = useState(''); // 비어있으면 현재 날짜
  const [initialInvestment, setInitialInvestment] = useState('300000');
  const [reinvestDividends, setReinvestDividends] = useState(false);
  const [tradingFeeRate, setTradingFeeRate] = useState('0');
  const [dividendTax, setDividendTax] = useState(false);
  const [fxMode, setFxMode] = useState<'auto' | 'manual'>('auto');
  const [manualPurchaseFxRate, setManualPurchaseFxRate] = useState('1300');
  const [manualCurrentFxRate, setManualCurrentFxRate] = useState('1350');

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
        fxMode={fxMode}
        setFxMode={setFxMode}
        manualPurchaseFxRate={manualPurchaseFxRate}
        setManualPurchaseFxRate={setManualPurchaseFxRate}
        manualCurrentFxRate={manualCurrentFxRate}
        setManualCurrentFxRate={setManualCurrentFxRate}
        reinvestDividends={reinvestDividends}
        setReinvestDividends={setReinvestDividends}
        tradingFeeRate={tradingFeeRate}
        setTradingFeeRate={setTradingFeeRate}
        dividendTax={dividendTax}
        setDividendTax={setDividendTax}
        supportedSymbols={supportedSymbols}
      />
    ),
    prepare: (ctx) => {
      const built = buildSimpleRequest(
        {
          purchaseDate,
          saleDate,
          initialInvestment,
          reinvestDividends,
          tradingFeeRate,
          dividendTax,
          fxMode,
          manualPurchaseFxRate,
          manualCurrentFxRate,
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
        fxMode,
        failureMessage: '백테스트 실행에 실패했습니다.',
      };
    },
    renderResult: (result) =>
      result.mode === 'simple' ? (
        <SimpleResult result={result} symbol={symbol} purchaseDate={purchaseDate} />
      ) : null,
  };
};
