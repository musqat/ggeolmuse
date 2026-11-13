import { useState } from 'react';
import { backtestApi } from '@services/api';
import { MaCrossStrategyForm } from '../../forms/MaCrossStrategyForm';
import { DcaConditionalResult } from '../../results/DcaConditionalResult';
import type { ModeController, SharedModeProps } from '../types';
import { commonFields } from '../commonFields';
import { sellRulesOf, strategyRun } from '../strategyRun';
import { buildMaCrossRequest } from './request';
import { MA_WINDOWS } from '../../shared/maWindows';
import { amountIssue, countErrors, windowIssues } from '../../settings/checks';
import { maCrossAbout, maCrossHow } from '../../settings/summary';
import { pickValues } from '../../settings/snapshot';
import { useSignalSymbol } from '../useSignalSymbol';
import { useTrailingStop } from '../useTrailingStop';
import { usePartialTakeProfit } from '../usePartialTakeProfit';

const LABEL = '원금';
const DEFAULTS = { amount: '1000000', short: MA_WINDOWS.short, long: MA_WINDOWS.long };

export const useMaCrossMode = (shared: SharedModeProps): ModeController => {
  const { symbol, supportedSymbols, period, options } = shared;
  const [investment, setInvestment] = useState(DEFAULTS.amount);
  const [shortWindow, setShortWindow] = useState(DEFAULTS.short);
  const [longWindow, setLongWindow] = useState(DEFAULTS.long);
  const { symbolField, periodField, symbolMessage, periodMessage } = commonFields(shared);
  const signal = useSignalSymbol(symbol, supportedSymbols);
  const trailing = useTrailingStop();
  const partial = usePartialTakeProfit();

  const amountMessage = amountIssue({ byShares: false, amount: investment, shares: '', label: LABEL });
  const windowMessages = windowIssues(shortWindow, longWindow);

  return {
    id: 'ma-cross',
    label: '이동평균 교차',
    description: maCrossAbout(shortWindow, longWindow),
    showsChartLink: true,
    form: (
      <MaCrossStrategyForm
        symbol={symbolField}
        period={periodField}
        amount={{ label: LABEL, amount: investment, onAmountChange: setInvestment, message: amountMessage }}
        windows={{
          short: shortWindow,
          onShortChange: setShortWindow,
          long: longWindow,
          onLongChange: setLongWindow,
          messages: windowMessages,
        }}
        signal={signal.field}
        trailing={trailing.field}
        partial={partial.field}
      />
    ),
    summary: {
      subject: symbol,
      how: maCrossHow({
        amount: investment,
        short: shortWindow,
        long: longWindow,
        signal: signal.signalSymbol,
        trailing: trailing.text,
        partial: partial.text,
      }),
    },
    errorCount: countErrors([
      symbolMessage,
      periodMessage,
      amountMessage,
      windowMessages.short,
      windowMessages.long,
      signal.message,
      ...trailing.messages,
      ...partial.messages,
    ]),
    reinvestLocked: true,
    reinvestNote: '이동평균 교차는 배당을 현금으로',
    saved: { amount: investment, short: shortWindow, long: longWindow, ...signal.saved, ...trailing.saved, ...partial.saved },
    load: (saved) => {
      const next = pickValues(DEFAULTS, saved);
      setInvestment(next.amount);
      setShortWindow(next.short);
      setLongWindow(next.long);
      signal.load(saved);
      trailing.load(saved);
      partial.load(saved);
    },
    prepare: (ctx) => {
      const built = buildMaCrossRequest(
        {
          startDate: period.start,
          endDate: period.end,
          investment,
          shortWindow,
          longWindow,
          signalSymbol: signal.signalSymbol,
          trailingStop: trailing.stop,
          trailingSell: trailing.sell,
          partialTakeProfits: partial.steps,
          ...options,
        },
        symbol,
        ctx,
      );
      if ('error' in built) return built;
      return strategyRun({
        request: built.request,
        call: backtestApi.runMaCrossStrategy,
        mode: 'ma-cross',
        fxMode: options.fxMode,
        failureMessage: '이동평균 교차 전략 실행에 실패했습니다.',
        windows: { short: built.request.shortWindow, long: built.request.longWindow },
        sellRules: sellRulesOf(built.request),
      });
    },
    renderResult: (result) =>
      result.mode === 'ma-cross' ? (
        <DcaConditionalResult
          kind="ma-cross"
          result={result}
          symbol={symbol}
          startDate={period.start}
          windows={result.windows}
          sellRules={result.sellRules}
        />
      ) : null,
  };
};
