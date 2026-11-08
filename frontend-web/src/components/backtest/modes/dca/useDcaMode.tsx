import { useState } from 'react';
import { backtestApi } from '@services/api';
import { DCAStrategyForm } from '../../forms/DCAStrategyForm';
import { DcaConditionalResult } from '../../results/DcaConditionalResult';
import type { ModeController, SharedModeProps } from '../types';
import { commonFields } from '../commonFields';
import { strategyRun } from '../strategyRun';
import { buildDcaRequest } from './request';
import { useBacktestOptions } from '../../shared/backtestOptions';
import {
  cadenceText,
  countRounds,
  dayOfMonth,
  parseMonths,
  resolveFirstPurchase,
  type DcaCadence,
  type FirstPick,
} from '../../shared/dcaSchedule';
import { useAmountUnit, useRoundCost } from '../../shared/amountUnit';
import { amountIssue, budgetIssue, countErrors, firstIssue, monthsIssue } from '../../settings/checks';
import { changedOptions, dcaHow } from '../../settings/summary';

const LABEL = '회당 금액';
const NO_PICK: FirstPick = { date: '', start: '' };

export const useDcaMode = (shared: SharedModeProps): ModeController => {
  const { symbol, period } = shared;
  const [amountPerPurchase, setAmountPerPurchase] = useState('100000');
  const amountUnit = useAmountUnit();
  // 주식 수 단위의 예산(비우면 제한 없음)
  const [budget, setBudget] = useState('');
  const [cadence, setCadence] = useState<DcaCadence>('MONTHLY');
  const [months, setMonths] = useState('3');
  const [firstPick, setFirstPick] = useState<FirstPick>(NO_PICK);
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
  const first = resolveFirstPurchase(period.start, cadence, firstPick) || period.start;
  // 고른 날의 며칠(그 달에 없어 말일로 옮겨도 그대로)
  const monthDay = firstPick.date ? dayOfMonth(firstPick.date) : undefined;
  const rounds = countRounds(cadence, parseMonths(months) ?? 1, first, end, monthDay);

  const amountMessage = amountIssue({ byShares, amount: amountPerPurchase, shares: amountUnit.shares, label: LABEL });
  const budgetMessage = byShares ? budgetIssue({ budget, perRound, byShares }) : null;
  const monthsMessage = monthsIssue(cadence, months);
  const firstMessage = firstIssue({ first, end, cadence, monthDay: monthDay ?? dayOfMonth(first) });

  return {
    id: 'dca',
    label: '적립식',
    showsChartLink: true,
    form: (
      <DCAStrategyForm
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
        budget={byShares ? { budget, onChange: setBudget, message: budgetMessage } : undefined}
        cadence={{ cadence, onCadenceChange: setCadence, months, onMonthsChange: setMonths, message: monthsMessage }}
        first={{
          value: first,
          picked: !!firstPick.date,
          min: period.start,
          max: end,
          onPick: (date) => setFirstPick({ date, start: period.start }),
          onFollow: () => setFirstPick(NO_PICK),
          message: firstMessage,
        }}
        options={options}
      />
    ),
    summary: {
      subject: symbol,
      how: dcaHow({
        unit: { byShares, amount: amountPerPurchase, shares: amountUnit.shares, perRound },
        cadenceLabel: cadenceText(cadence, months, first, monthDay),
        rounds,
        daily: cadence === 'DAILY',
        budget: byShares ? budget : '',
      }),
      changed: changedOptions(options, false),
    },
    errorCount: countErrors([symbolMessage, periodMessage, amountMessage, budgetMessage, monthsMessage, firstMessage]),
    prepare: (ctx) => {
      const built = buildDcaRequest(
        {
          startDate: period.start,
          endDate: period.end,
          amountPerPurchase,
          unit: amountUnit.unit,
          shares: amountUnit.shares,
          cadence,
          months,
          firstPurchaseDate: firstPick.date,
          firstPickedStart: firstPick.start,
          budget: byShares ? budget : '',
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
        <DcaConditionalResult kind="dca" result={result} symbol={symbol} startDate={period.start} />
      ) : null,
  };
};
