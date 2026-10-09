import { useState } from 'react';
import { backtestApi } from '@services/api';
import { DCAStrategyForm } from '../../forms/DCAStrategyForm';
import { DcaConditionalResult } from '../../results/DcaConditionalResult';
import type { ModeController, SharedSymbolProps } from '../types';
import { buildDcaRequest } from './request';

export const useDcaMode = ({
  symbol,
  setSymbol,
  supportedSymbols,
}: SharedSymbolProps): ModeController => {
  const [startDate, setStartDate] = useState('2023-01-01');
  const [endDate, setEndDate] = useState(''); // 비어있으면 현재 날짜
  const [monthlyAmount, setMonthlyAmount] = useState('100000');
  const [purchaseDay, setPurchaseDay] = useState('15');
  const [investmentInterval, setInvestmentInterval] = useState('1');
  const [reinvestDividends, setReinvestDividends] = useState(false);
  const [tradingFeeRate, setTradingFeeRate] = useState('0');
  const [dividendTax, setDividendTax] = useState(false);
  const [fxMode, setFxMode] = useState<'auto' | 'manual'>('auto');
  const [manualPurchaseFxRate, setManualPurchaseFxRate] = useState('1300');
  const [manualCurrentFxRate, setManualCurrentFxRate] = useState('1350');

  return {
    id: 'dca',
    label: '적립식',
    showsChartLink: true,
    form: (
      <DCAStrategyForm
        symbol={symbol}
        setSymbol={setSymbol}
        dcaStartDate={startDate}
        setDcaStartDate={setStartDate}
        dcaEndDate={endDate}
        setDcaEndDate={setEndDate}
        monthlyAmount={monthlyAmount}
        setMonthlyAmount={setMonthlyAmount}
        purchaseDay={purchaseDay}
        setPurchaseDay={setPurchaseDay}
        investmentInterval={investmentInterval}
        setInvestmentInterval={setInvestmentInterval}
        dcaFxMode={fxMode}
        setDcaFxMode={setFxMode}
        dcaManualPurchaseFxRate={manualPurchaseFxRate}
        setDcaManualPurchaseFxRate={setManualPurchaseFxRate}
        dcaManualCurrentFxRate={manualCurrentFxRate}
        setDcaManualCurrentFxRate={setManualCurrentFxRate}
        dcaReinvestDividends={reinvestDividends}
        setDcaReinvestDividends={setReinvestDividends}
        dcaTradingFeeRate={tradingFeeRate}
        setDcaTradingFeeRate={setTradingFeeRate}
        dcaDividendTax={dividendTax}
        setDcaDividendTax={setDividendTax}
        supportedSymbols={supportedSymbols}
      />
    ),
    prepare: (ctx) => {
      const built = buildDcaRequest(
        {
          startDate,
          endDate,
          monthlyAmount,
          purchaseDay,
          investmentInterval,
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
          ...(await backtestApi.runDcaStrategy(request)).data,
          mode: 'dca' as const,
        }),
        historyType: 'STRATEGY_SIMULATION',
        params: request,
        fxMode,
        failureMessage: 'DCA 전략 실행에 실패했습니다.',
      };
    },
    // conditionalStartDate 는 mode 가 dca 면 읽지 않는다
    renderResult: (result) =>
      result.mode === 'dca' ? (
        <DcaConditionalResult
          result={result}
          mode="dca"
          symbol={symbol}
          dcaStartDate={startDate}
          conditionalStartDate=""
        />
      ) : null,
  };
};
