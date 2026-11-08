import { useState } from 'react';
import { backtestApi, type PriceDirection } from '@services/api';
import { ConditionalStrategyForm } from '../../forms/ConditionalStrategyForm';
import { DcaConditionalResult } from '../../results/DcaConditionalResult';
import type { ModeController, SharedModeProps } from '../types';
import { commonFields } from '../commonFields';
import { strategyRun } from '../strategyRun';
import { buildConditionalRequest } from './request';
import { useBacktestOptions } from '../../shared/backtestOptions';
import { useAmountUnit, useRoundCost } from '../../shared/amountUnit';
import { hasSellRates } from '../../shared/sellRates';
import { countRounds } from '../../shared/dcaSchedule';
import { amountIssue, budgetIssue, countErrors, maxIssue, percentIssue, sellIssue } from '../../settings/checks';
import { changedOptions, conditionalHow } from '../../settings/summary';

const LABEL = '회당 금액';

export const useConditionalMode = (shared: SharedModeProps): ModeController => {
  const { symbol, period } = shared;
  const [amountPerPurchase, setAmountPerPurchase] = useState('100000');
  const amountUnit = useAmountUnit();
  // 예산 · 최대 횟수는 비우면 제한 없음
  const [totalInvestment, setTotalInvestment] = useState('1000000');
  const [maxPurchases, setMaxPurchases] = useState('');
  const [direction, setDirection] = useState<PriceDirection>('DROP');
  const [dropPercentage, setDropPercentage] = useState('5');
  const [takeProfit, setTakeProfit] = useState(''); // 비우면 익절 안 함
  const [stopLoss, setStopLoss] = useState(''); // 비우면 손절 안 함
  const options = useBacktestOptions();
  const { end, symbolField, periodField, symbolMessage, periodMessage } = commonFields(shared);

  const byShares = amountUnit.unit === 'SHARES';
  const perRound = useRoundCost({
    symbol,
    startDate: period.start,
    unit: amountUnit.unit,
    shares: amountUnit.shares,
    amount: amountPerPurchase,
    options,
  });
  const sells = hasSellRates(takeProfit, stopLoss);

  const amountMessage = amountIssue({ byShares, amount: amountPerPurchase, shares: amountUnit.shares, label: LABEL });
  const budgetMessage = budgetIssue({ budget: totalInvestment, perRound, byShares });
  const percentMessage = percentIssue(dropPercentage);
  const sellMessage = sellIssue(takeProfit, stopLoss);
  const maxMessage = maxIssue(maxPurchases);

  return {
    id: 'conditional',
    label: '조건부',
    showsChartLink: true,
    form: (
      <ConditionalStrategyForm
        symbol={symbolField}
        period={periodField}
        amount={{
          label: LABEL,
          sharesLabel: '회당 주식 수',
          amount: amountPerPurchase,
          onAmountChange: setAmountPerPurchase,
          amountUnit,
          perRound: byShares ? perRound : undefined,
          message: amountMessage,
        }}
        budget={{ budget: totalInvestment, onChange: setTotalInvestment, message: budgetMessage }}
        condition={{
          direction,
          onDirectionChange: setDirection,
          percent: dropPercentage,
          onPercentChange: setDropPercentage,
          message: percentMessage,
        }}
        sell={{
          takeProfit,
          onTakeProfitChange: setTakeProfit,
          stopLoss,
          onStopLossChange: setStopLoss,
          message: sellMessage,
        }}
        max={{
          value: maxPurchases,
          onChange: setMaxPurchases,
          placeholder: totalInvestment.trim() ? '예산 한도까지' : '제한 없음',
          message: maxMessage,
        }}
        options={options}
        reinvestLocked={sells}
      />
    ),
    summary: {
      subject: symbol,
      how: conditionalHow({
        percent: dropPercentage,
        direction,
        unit: { byShares, amount: amountPerPurchase, shares: amountUnit.shares, perRound },
        budget: totalInvestment,
        maxPurchases,
        weekdays: countRounds('DAILY', 1, period.start, end),
        takeProfit,
        stopLoss,
      }),
      changed: changedOptions(options, sells),
    },
    errorCount: countErrors([symbolMessage, periodMessage, amountMessage, budgetMessage, percentMessage, sellMessage, maxMessage]),
    prepare: (ctx) => {
      const built = buildConditionalRequest(
        {
          startDate: period.start,
          endDate: period.end,
          amountPerPurchase,
          unit: amountUnit.unit,
          shares: amountUnit.shares,
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
        <DcaConditionalResult kind="conditional" result={result} symbol={symbol} startDate={period.start} />
      ) : null,
  };
};
