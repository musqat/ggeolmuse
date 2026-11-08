import { useState } from 'react';
import { backtestApi } from '@services/api';
import { SymbolComparisonForm } from '../../forms/SymbolComparisonForm';
import { SymbolComparisonResult } from '../../results/SymbolComparisonResult';
import type { ModeController, SharedModeProps } from '../types';
import { commonFields } from '../commonFields';
import { buildSymbolComparisonRequest } from './request';
import { useBacktestOptions } from '../../shared/backtestOptions';
import { amountIssue, countErrors, symbolIssue, symbolsIssue } from '../../settings/checks';
import { changedOptions } from '../../settings/summary';
import { formatReadableWon } from '../../../../utils/format';

const LABEL = '종목마다 금액';
const MAX_SYMBOLS = 10;

// 요약 첫 줄의 종목(셋까지, 나머지는 외 N)
const subjectOf = (symbols: string[]) =>
  `${symbols.slice(0, 3).join(', ')}${symbols.length > 3 ? ` 외 ${symbols.length - 3}` : ''}` || '종목';

export const useSymbolComparisonMode = (shared: SharedModeProps): ModeController => {
  const { symbol, supportedSymbols, period } = shared;
  const [symbols, setSymbols] = useState<string[]>(['AAPL', 'MSFT']);
  const [investment, setInvestment] = useState('1000000');
  const options = useBacktestOptions();
  const { periodField, periodMessage } = commonFields(shared);

  const symbolsMessage = symbolsIssue(symbols);
  const amountMessage = amountIssue({ byShares: false, amount: investment, shares: '', label: LABEL });

  const addSymbol = (next: string) => {
    if (symbols.length < MAX_SYMBOLS && !symbols.includes(next)) setSymbols([...symbols, next]);
  };

  return {
    id: 'compare-symbols',
    label: '종목 비교',
    showsChartLink: false,
    form: (
      <SymbolComparisonForm
        symbols={{
          symbols,
          onAdd: addSymbol,
          onRemove: (removed) => setSymbols(symbols.filter((s) => s !== removed)),
          supportedSymbols,
          message: symbolsMessage,
        }}
        period={periodField}
        amount={{ label: LABEL, amount: investment, onAmountChange: setInvestment, message: amountMessage }}
        options={options}
      />
    ),
    summary: {
      subject: subjectOf(symbols),
      how: `종목마다 ${formatReadableWon(parseFloat(investment))}`,
      changed: changedOptions(options, false),
    },
    errorCount: countErrors([symbolsMessage, periodMessage, amountMessage]),
    // 보던 종목을 맨 앞에(목록에 있는 종목만, 10개까지)
    onEnter: () => {
      if (symbolIssue(symbol, supportedSymbols) || symbols.includes(symbol)) return;
      setSymbols([symbol, ...symbols].slice(0, MAX_SYMBOLS));
    },
    prepare: (ctx) => {
      const built = buildSymbolComparisonRequest(
        {
          symbols,
          purchaseDate: period.start,
          saleDate: period.end,
          investment,
          ...options,
        },
        ctx,
      );
      if ('error' in built) return built;
      const { request } = built;
      return {
        execute: async () => ({
          ...(await backtestApi.compareSymbols(request)).data,
          mode: 'compare-symbols' as const,
        }),
        historyType: 'COMPARISON',
        params: request,
        fxMode: options.fxMode,
        failureMessage: '종목 비교 실행에 실패했습니다.',
      };
    },
    renderResult: (result) =>
      result.mode === 'compare-symbols' ? (
        <SymbolComparisonResult result={result} comparePurchaseDate={period.start} compareSaleDate={period.end} />
      ) : null,
  };
};
