import { useState } from 'react';
import { backtestApi } from '@services/api';
import { DCAStrategyForm } from '../../forms/DCAStrategyForm';
import { DcaConditionalResult } from '../../results/DcaConditionalResult';
import type { ModeController, SharedSymbolProps } from '../types';
import { strategyRun } from '../strategyRun';
import { buildDcaRequest } from './request';
import { useBacktestOptions } from '../../shared/backtestOptions';

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
  const options = useBacktestOptions();

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
        options={options}
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
          ...options,
        },
        symbol,
        ctx,
      );
      if ('error' in built) return built;
      return strategyRun({
        request: built.request,
        call: backtestApi.runDcaStrategy,
        mode: 'dca',
        fxMode: options.fxMode,
        failureMessage: 'DCA 전략 실행에 실패했습니다.',
      });
    },
    renderResult: (result) =>
      result.mode === 'dca' ? (
        <DcaConditionalResult result={result} symbol={symbol} startDate={startDate} />
      ) : null,
  };
};
