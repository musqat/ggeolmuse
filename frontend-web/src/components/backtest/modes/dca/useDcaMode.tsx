import { useState } from 'react';
import { backtestApi } from '@services/api';
import { DCAStrategyForm } from '../../forms/DCAStrategyForm';
import { DcaConditionalResult } from '../../results/DcaConditionalResult';
import type { ModeController, SharedSymbolProps } from '../types';
import { strategyRun } from '../strategyRun';
import { buildDcaRequest } from './request';
import { useBacktestOptions } from '../../shared/backtestOptions';
import type { DcaCadence } from '../../shared/dcaSchedule';
import { useAmountUnit } from '../../shared/amountUnit';

export const useDcaMode = ({
  symbol,
  setSymbol,
  supportedSymbols,
}: SharedSymbolProps): ModeController => {
  const [startDate, setStartDate] = useState('2023-01-01');
  const [endDate, setEndDate] = useState(''); // 비어있으면 현재 날짜
  const [amountPerPurchase, setAmountPerPurchase] = useState('100000');
  const amountUnit = useAmountUnit();
  const [cadence, setCadence] = useState<DcaCadence>('MONTHLY');
  const [months, setMonths] = useState('3');
  const [firstPurchaseDate, setFirstPurchaseDate] = useState(''); // 비어있으면 시작일
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
        amountPerPurchase={amountPerPurchase}
        setAmountPerPurchase={setAmountPerPurchase}
        amountUnit={amountUnit}
        cadence={cadence}
        setCadence={setCadence}
        months={months}
        setMonths={setMonths}
        firstPurchaseDate={firstPurchaseDate}
        setFirstPurchaseDate={setFirstPurchaseDate}
        options={options}
        supportedSymbols={supportedSymbols}
      />
    ),
    prepare: (ctx) => {
      const built = buildDcaRequest(
        {
          startDate,
          endDate,
          amountPerPurchase,
          unit: amountUnit.unit,
          shares: amountUnit.shares,
          cadence,
          months,
          firstPurchaseDate,
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
        <DcaConditionalResult kind="dca" result={result} symbol={symbol} startDate={startDate} />
      ) : null,
  };
};
