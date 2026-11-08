import { useState } from 'react';
import { backtestApi, type PriceDirection } from '@services/api';
import { ConditionalStrategyForm } from '../../forms/ConditionalStrategyForm';
import { DcaConditionalResult } from '../../results/DcaConditionalResult';
import type { ModeController, SharedSymbolProps } from '../types';
import { strategyRun } from '../strategyRun';
import { buildConditionalRequest } from './request';
import { useBacktestOptions } from '../../shared/backtestOptions';

export const useConditionalMode = ({
  symbol,
  setSymbol,
  supportedSymbols,
}: SharedSymbolProps): ModeController => {
  const [startDate, setStartDate] = useState('2023-01-01');
  const [endDate, setEndDate] = useState(''); // 비어있으면 현재 날짜
  const [amountPerPurchase, setAmountPerPurchase] = useState('100000');
  // 예산 · 최대 횟수는 비우면 제한 없음
  const [totalInvestment, setTotalInvestment] = useState('1000000');
  const [maxPurchases, setMaxPurchases] = useState('');
  const [direction, setDirection] = useState<PriceDirection>('DROP');
  const [dropPercentage, setDropPercentage] = useState('5');
  const [takeProfit, setTakeProfit] = useState(''); // 비우면 익절 안 함
  const [stopLoss, setStopLoss] = useState(''); // 비우면 손절 안 함
  const options = useBacktestOptions();

  return {
    id: 'conditional',
    label: '조건부',
    showsChartLink: true,
    form: (
      <ConditionalStrategyForm
        symbol={symbol}
        setSymbol={setSymbol}
        conditionalStartDate={startDate}
        setConditionalStartDate={setStartDate}
        conditionalEndDate={endDate}
        setConditionalEndDate={setEndDate}
        amountPerPurchase={amountPerPurchase}
        setAmountPerPurchase={setAmountPerPurchase}
        totalInvestment={totalInvestment}
        setTotalInvestment={setTotalInvestment}
        maxPurchases={maxPurchases}
        setMaxPurchases={setMaxPurchases}
        direction={direction}
        setDirection={setDirection}
        dropPercentage={dropPercentage}
        setDropPercentage={setDropPercentage}
        takeProfit={takeProfit}
        setTakeProfit={setTakeProfit}
        stopLoss={stopLoss}
        setStopLoss={setStopLoss}
        options={options}
        supportedSymbols={supportedSymbols}
      />
    ),
    prepare: (ctx) => {
      const built = buildConditionalRequest(
        {
          startDate,
          endDate,
          amountPerPurchase,
          totalInvestment,
          maxPurchases,
          direction,
          dropPercentage,
          takeProfit,
          stopLoss,
          ...options,
        },
        symbol,
        ctx,
      );
      if ('error' in built) return built;
      return strategyRun({
        request: built.request,
        call: backtestApi.runConditionalStrategy,
        mode: 'conditional',
        fxMode: options.fxMode,
        failureMessage: '조건부 전략 실행에 실패했습니다.',
      });
    },
    renderResult: (result) =>
      result.mode === 'conditional' ? (
        <DcaConditionalResult kind="conditional" result={result} symbol={symbol} startDate={startDate} />
      ) : null,
  };
};
