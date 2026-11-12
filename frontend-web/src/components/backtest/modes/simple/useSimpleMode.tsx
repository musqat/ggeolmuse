import { useState } from 'react';
import { backtestApi } from '@services/api';
import { SimpleStrategyForm } from '../../forms/SimpleStrategyForm';
import { SimpleResult } from '../../results/SimpleResult';
import type { ModeController, SharedModeProps } from '../types';
import { commonFields } from '../commonFields';
import { buildSimpleRequest } from './request';
import { AMOUNT_UNITS, useAmountUnit, useRoundCost } from '../../shared/amountUnit';
import { amountIssue, countErrors } from '../../settings/checks';
import { simpleHow } from '../../settings/summary';
import { oneOf, pickValues } from '../../settings/snapshot';

const LABEL = '금액';
const DEFAULTS = { amount: '300000', unit: 'AMOUNT', shares: '1' };

export const useSimpleMode = (shared: SharedModeProps): ModeController => {
  const { symbol, period, options } = shared;
  const [initialInvestment, setInitialInvestment] = useState(DEFAULTS.amount);
  const amountUnit = useAmountUnit();
  const { symbolField, periodField, symbolMessage, periodMessage } = commonFields(shared, { manualAction: true });
  const byShares = amountUnit.unit === 'SHARES';
  const { perRound, defaultFx } = useRoundCost({
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
    description: '시작일에 한 번에 사서 끝까지 보유',
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
          defaultFx,
          message: amountMessage,
        }}
      />
    ),
    summary: {
      subject: symbol,
      how: simpleHow({ byShares, amount: initialInvestment, shares: amountUnit.shares, perRound, defaultFx }),
    },
    errorCount: countErrors([symbolMessage, periodMessage, amountMessage]),
    saved: { amount: initialInvestment, unit: amountUnit.unit, shares: amountUnit.shares },
    load: (saved) => {
      const next = pickValues(DEFAULTS, saved);
      setInitialInvestment(next.amount);
      amountUnit.setUnit(oneOf(next.unit, AMOUNT_UNITS, 'AMOUNT'));
      amountUnit.setShares(next.shares);
    },
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
