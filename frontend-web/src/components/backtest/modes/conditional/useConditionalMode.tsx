import { useState } from 'react';
import { backtestApi, type PriceDirection } from '@services/api';
import { ConditionalStrategyForm } from '../../forms/ConditionalStrategyForm';
import { DcaConditionalResult } from '../../results/DcaConditionalResult';
import type { ModeController, SharedModeProps } from '../types';
import { commonFields } from '../commonFields';
import { strategyRun } from '../strategyRun';
import { buildConditionalRequest } from './request';
import { AMOUNT_UNITS, useAmountUnit, useRoundCost } from '../../shared/amountUnit';
import { hasSellRates } from '../../shared/sellRates';
import { DIRECTION_ORDER } from '../../shared/priceDirection';
import { countRounds } from '../../shared/dcaSchedule';
import { amountIssue, budgetIssue, countErrors, maxIssue, percentIssue, sellIssue } from '../../settings/checks';
import { conditionalHow } from '../../settings/summary';
import { oneOf, pickValues } from '../../settings/snapshot';

const LABEL = '회당 금액';
const DEFAULTS = {
  amount: '100000',
  unit: 'AMOUNT',
  shares: '1',
  budget: '1000000',
  maxPurchases: '',
  direction: 'DROP',
  percent: '5',
  takeProfit: '',
  stopLoss: '',
};

export const useConditionalMode = (shared: SharedModeProps): ModeController => {
  const { symbol, period, options } = shared;
  const [amountPerPurchase, setAmountPerPurchase] = useState(DEFAULTS.amount);
  const amountUnit = useAmountUnit();
  // 예산 · 최대 횟수는 비우면 제한 없음
  const [totalInvestment, setTotalInvestment] = useState(DEFAULTS.budget);
  const [maxPurchases, setMaxPurchases] = useState(DEFAULTS.maxPurchases);
  const [direction, setDirection] = useState<PriceDirection>('DROP');
  const [dropPercentage, setDropPercentage] = useState(DEFAULTS.percent);
  const [takeProfit, setTakeProfit] = useState(DEFAULTS.takeProfit); // 비우면 익절 안 함
  const [stopLoss, setStopLoss] = useState(DEFAULTS.stopLoss); // 비우면 손절 안 함
  const { end, symbolField, periodField, symbolMessage, periodMessage } = commonFields(shared);

  const byShares = amountUnit.unit === 'SHARES';
  const { perRound, defaultFx } = useRoundCost({
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
          defaultFx,
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
      />
    ),
    summary: {
      subject: symbol,
      how: conditionalHow({
        percent: dropPercentage,
        direction,
        unit: { byShares, amount: amountPerPurchase, shares: amountUnit.shares, perRound, defaultFx },
        budget: totalInvestment,
        maxPurchases,
        weekdays: countRounds('DAILY', 1, period.start, end),
        takeProfit,
        stopLoss,
      }),
    },
    errorCount: countErrors([symbolMessage, periodMessage, amountMessage, budgetMessage, percentMessage, sellMessage, maxMessage]),
    reinvestLocked: sells,
    saved: {
      amount: amountPerPurchase,
      unit: amountUnit.unit,
      shares: amountUnit.shares,
      budget: totalInvestment,
      maxPurchases,
      direction,
      percent: dropPercentage,
      takeProfit,
      stopLoss,
    },
    load: (saved) => {
      const next = pickValues(DEFAULTS, saved);
      setAmountPerPurchase(next.amount);
      amountUnit.setUnit(oneOf(next.unit, AMOUNT_UNITS, 'AMOUNT'));
      amountUnit.setShares(next.shares);
      setTotalInvestment(next.budget);
      setMaxPurchases(next.maxPurchases);
      setDirection(oneOf(next.direction, DIRECTION_ORDER, 'DROP'));
      setDropPercentage(next.percent);
      setTakeProfit(next.takeProfit);
      setStopLoss(next.stopLoss);
    },
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
        budget: built.request.totalInvestment,
      });
    },
    renderResult: (result) =>
      result.mode === 'conditional' ? (
        <DcaConditionalResult
          kind="conditional"
          result={result}
          symbol={symbol}
          startDate={period.start}
          budget={result.budget}
          sellAtEnd={result.periodEnd === 'SELL_AT_END'}
        />
      ) : null,
  };
};
