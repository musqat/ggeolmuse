import { useState } from 'react';
import { backtestApi } from '@services/api';
import { SymbolComparisonForm } from '../../forms/SymbolComparisonForm';
import { SymbolComparisonResult } from '../../results/SymbolComparisonResult';
import type { ModeController, SharedModeProps } from '../types';
import { commonFields } from '../commonFields';
import { buildSymbolComparisonRequest } from './request';
import { amountIssue, countErrors, symbolIssue, symbolsIssue } from '../../settings/checks';
import { pickValues } from '../../settings/snapshot';
import { formatReadableWon } from '../../../../utils/format';

const LABEL = '종목마다 금액';
const MAX_SYMBOLS = 10;
const DEFAULTS = { symbols: ['AAPL', 'MSFT'], investment: '1000000' };

// 저장한 종목 목록(글자만, 겹치지 않게, 10개까지)
const savedSymbols = (symbols: unknown[]) =>
  [...new Set(symbols.filter((item): item is string => typeof item === 'string' && item.trim() !== ''))].slice(0, MAX_SYMBOLS);

// 요약 첫 줄의 종목(셋까지, 나머지는 외 N)
const subjectOf = (symbols: string[]) =>
  `${symbols.slice(0, 3).join(', ')}${symbols.length > 3 ? ` 외 ${symbols.length - 3}` : ''}` || '종목';

export const useSymbolComparisonMode = (shared: SharedModeProps): ModeController => {
  const { symbol, supportedSymbols, period, options } = shared;
  const [symbols, setSymbols] = useState<string[]>(DEFAULTS.symbols);
  const [investment, setInvestment] = useState(DEFAULTS.investment);
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
      />
    ),
    summary: {
      subject: subjectOf(symbols),
      how: `종목마다 ${formatReadableWon(parseFloat(investment))}`,
    },
    errorCount: countErrors([symbolsMessage, periodMessage, amountMessage]),
    saved: { symbols, investment },
    load: (saved) => {
      const next = pickValues(DEFAULTS, saved);
      setSymbols(savedSymbols(next.symbols));
      setInvestment(next.investment);
    },
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
