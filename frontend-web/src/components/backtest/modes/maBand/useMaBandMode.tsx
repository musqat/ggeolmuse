import { useState } from 'react';
import { backtestApi } from '@services/api';
import { MaBandStrategyForm } from '../../forms/MaBandStrategyForm';
import { DcaConditionalResult } from '../../results/DcaConditionalResult';
import type { ModeController, SharedModeProps } from '../types';
import { commonFields } from '../commonFields';
import { sellRulesOf, strategyRun } from '../strategyRun';
import { buildMaBandRequest } from './request';
import { MA_BAND } from '../../shared/maBand';
import { amountIssue, bandIssues, countErrors } from '../../settings/checks';
import { maBandAbout, maBandHow } from '../../settings/summary';
import { pickValues } from '../../settings/snapshot';
import { singleTaxSale } from '../../shared/taxSale';
import { useSignalSymbol } from '../useSignalSymbol';
import { useTrailingStop } from '../useTrailingStop';
import { usePartialTakeProfit } from '../usePartialTakeProfit';
import { useExecutionTiming } from '../useExecutionTiming';

const LABEL = '원금';
const DEFAULTS = { amount: '1000000', window: MA_BAND.window, upper: MA_BAND.upper, lower: MA_BAND.lower };

export const useMaBandMode = (shared: SharedModeProps): ModeController => {
  const { symbol, supportedSymbols, period, options } = shared;
  const [investment, setInvestment] = useState(DEFAULTS.amount);
  const [bandWindow, setBandWindow] = useState(DEFAULTS.window);
  const [upper, setUpper] = useState(DEFAULTS.upper);
  const [lower, setLower] = useState(DEFAULTS.lower);
  const { symbolField, periodField, symbolMessage, periodMessage } = commonFields(shared);
  const signal = useSignalSymbol(symbol, supportedSymbols);
  const trailing = useTrailingStop();
  const partial = usePartialTakeProfit();
  const execution = useExecutionTiming();

  const amountMessage = amountIssue({ byShares: false, amount: investment, shares: '', label: LABEL });
  const bandMessages = bandIssues(bandWindow, upper, lower);

  return {
    id: 'ma-band',
    label: '이동평균 밴드',
    description: maBandAbout(bandWindow, upper, lower),
    showsChartLink: true,
    form: (
      <MaBandStrategyForm
        symbol={symbolField}
        period={periodField}
        amount={{ label: LABEL, amount: investment, onAmountChange: setInvestment, message: amountMessage }}
        band={{
          window: bandWindow,
          onWindowChange: setBandWindow,
          upper,
          onUpperChange: setUpper,
          lower,
          onLowerChange: setLower,
          messages: bandMessages,
        }}
        signal={signal.field}
        execution={execution.field}
        trailing={trailing.field}
        partial={partial.field}
      />
    ),
    summary: {
      subject: symbol,
      how: maBandHow({
        amount: investment,
        window: bandWindow,
        upper,
        lower,
        signal: signal.signalSymbol,
        execution: execution.text,
        trailing: trailing.text,
        partial: partial.text,
      }),
    },
    errorCount: countErrors([
      symbolMessage,
      periodMessage,
      amountMessage,
      bandMessages.window,
      bandMessages.upper,
      bandMessages.lower,
      signal.message,
      ...trailing.messages,
      ...partial.messages,
    ]),
    reinvestLocked: true,
    reinvestNote: '밴드는 배당을 현금으로',
    taxSale: singleTaxSale(options),
    harvest: true,
    saved: {
      amount: investment,
      window: bandWindow,
      upper,
      lower,
      ...signal.saved,
      ...execution.saved,
      ...trailing.saved,
      ...partial.saved,
    },
    load: (saved) => {
      const next = pickValues(DEFAULTS, saved);
      setInvestment(next.amount);
      setBandWindow(next.window);
      setUpper(next.upper);
      setLower(next.lower);
      signal.load(saved);
      execution.load(saved);
      trailing.load(saved);
      partial.load(saved);
    },
    prepare: (ctx) => {
      const built = buildMaBandRequest(
        {
          startDate: period.start,
          endDate: period.end,
          investment,
          bandWindow,
          upperPercent: upper,
          lowerPercent: lower,
          signalSymbol: signal.signalSymbol,
          executionTiming: execution.timing,
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
        call: backtestApi.runMaBandStrategy,
        mode: 'ma-band',
        fxMode: options.fxMode,
        failureMessage: '이동평균 밴드 전략 실행에 실패했습니다.',
        band: { window: built.request.bandWindow, upperRate: built.request.upperRate, lowerRate: built.request.lowerRate },
        sellRules: sellRulesOf(built.request),
      });
    },
    renderResult: (result) =>
      result.mode === 'ma-band' ? (
        <DcaConditionalResult
          kind="ma-band"
          result={result}
          symbol={symbol}
          startDate={period.start}
          band={result.band}
          sellRules={result.sellRules}
        />
      ) : null,
  };
};
