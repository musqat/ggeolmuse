import { useState } from 'react';
import { backtestApi } from '@services/api';
import { ConditionalStrategyForm } from '../../forms/ConditionalStrategyForm';
import { DcaConditionalResult } from '../../results/DcaConditionalResult';
import type { ModeController, SharedSymbolProps } from '../types';
import { buildConditionalRequest } from './request';
import { useBacktestOptions } from '../../shared/backtestOptions';

export const useConditionalMode = ({
  symbol,
  setSymbol,
  supportedSymbols,
}: SharedSymbolProps): ModeController => {
  const [startDate, setStartDate] = useState('2023-01-01');
  const [endDate, setEndDate] = useState(''); // 비어있으면 현재 날짜
  const [investmentMode, setInvestmentMode] = useState<'TOTAL_BUDGET' | 'PER_PURCHASE'>('TOTAL_BUDGET');
  const [totalInvestment, setTotalInvestment] = useState('1000000');
  const [amountPerPurchase, setAmountPerPurchase] = useState('100000');
  const [maxPurchases, setMaxPurchases] = useState('20');
  const [dropPercentage, setDropPercentage] = useState('5');
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
        investmentMode={investmentMode}
        setInvestmentMode={setInvestmentMode}
        totalInvestment={totalInvestment}
        setTotalInvestment={setTotalInvestment}
        amountPerPurchase={amountPerPurchase}
        setAmountPerPurchase={setAmountPerPurchase}
        maxPurchases={maxPurchases}
        setMaxPurchases={setMaxPurchases}
        dropPercentage={dropPercentage}
        setDropPercentage={setDropPercentage}
        options={options}
        supportedSymbols={supportedSymbols}
      />
    ),
    prepare: (ctx) => {
      const built = buildConditionalRequest(
        {
          startDate,
          endDate,
          investmentMode,
          totalInvestment,
          amountPerPurchase,
          maxPurchases,
          dropPercentage,
          ...options,
        },
        symbol,
        ctx,
      );
      if ('error' in built) return built;
      const { request } = built;
      return {
        execute: async () => ({
          ...(await backtestApi.runConditionalStrategy(request)).data,
          mode: 'conditional' as const,
        }),
        historyType: 'STRATEGY_SIMULATION',
        params: request,
        fxMode: options.fxMode,
        failureMessage: '조건부 전략 실행에 실패했습니다.',
      };
    },
    // dcaStartDate 는 mode 가 conditional 이면 읽지 않는다
    renderResult: (result) =>
      result.mode === 'conditional' ? (
        <DcaConditionalResult
          result={result}
          mode="conditional"
          symbol={symbol}
          dcaStartDate=""
          conditionalStartDate={startDate}
        />
      ) : null,
  };
};
