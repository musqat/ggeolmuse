import { useState } from 'react';
import { backtestApi } from '@services/api';
import { SimpleStrategyForm } from '../../forms/SimpleStrategyForm';
import { SimpleResult } from '../../results/SimpleResult';
import type { ModeController, SharedModeProps } from '../types';
import { commonFields } from '../commonFields';
import { buildSimpleRequest } from './request';
import { useBacktestOptions } from '../../shared/backtestOptions';
import { useAmountUnit, useRoundCost } from '../../shared/amountUnit';
import { amountIssue, countErrors } from '../../settings/checks';
import { changedOptions, simpleHow } from '../../settings/summary';

const LABEL = '금액';

export const useSimpleMode = (shared: SharedModeProps): ModeController => {
  const { symbol, period } = shared;
  const [initialInvestment, setInitialInvestment] = useState('300000');
  const amountUnit = useAmountUnit();
  const options = useBacktestOptions();
  const { symbolField, periodField, symbolMessage, periodMessage } = commonFields(shared);
  const byShares = amountUnit.unit === 'SHARES';
  const perRound = useRoundCost({
    symbol,
    startDate: period.start,
    unit: amountUnit.unit,
    shares: amountUnit.shares,
    amount: initialInvestment,
    options,
  });
  const amountMessage = amountIssue({ byShares, amount: initialInvestment, shares: amountUnit.shares, label: LABEL });

  return {
    id: 'simple',
    label: '단순',
    showsChartLink: true,
    form: (
      <SimpleStrategyForm
        symbol={symbolField}
        period={periodField}
        amount={{
          label: LABEL,
          sharesLabel: '주식 수',
          amount: initialInvestment,
          onAmountChange: setInitialInvestment,
          amountUnit,
          perRound: byShares ? perRound : undefined,
          message: amountMessage,
        }}
        options={options}
      />
    ),
    summary: {
      subject: symbol,
      how: simpleHow({ byShares, amount: initialInvestment, shares: amountUnit.shares, perRound }),
      changed: changedOptions(options, false),
    },
    errorCount: countErrors([symbolMessage, periodMessage, amountMessage]),
    prepare: (ctx) => {
      const built = buildSimpleRequest(
        {
          purchaseDate: period.start,
          saleDate: period.end,
          initialInvestment,
          unit: amountUnit.unit,
          shares: amountUnit.shares,
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
        <SimpleResult result={result} symbol={symbol} purchaseDate={period.start} />
      ) : null,
  };
};
