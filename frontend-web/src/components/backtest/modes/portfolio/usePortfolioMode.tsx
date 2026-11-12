import { useState } from 'react';
import { backtestApi, type PortfolioAllocation, type PortfolioMethod, type PortfolioRebalance } from '@services/api';
import type { ModeController, SharedModeProps } from '../types';
import { commonFields } from '../commonFields';
import { PortfolioForm } from './PortfolioForm';
import { PortfolioResult } from '../../results/portfolio/PortfolioResult';
import { buildPortfolioRequest } from './request';
import {
  cadenceText,
  countRounds,
  dayOfMonth,
  parseMonths,
  resolveFirstPurchase,
  type DcaCadence,
  type FirstPick,
} from '../../shared/dcaSchedule';
import {
  FIRST_AMOUNT,
  hasDcaRow,
  isAllDca,
  newRow,
  rowAmounts,
  rowStrategy,
  rowsSellText,
  rowsSubject,
  savedRows,
  type PortfolioRow,
} from '../../shared/portfolioRows';
import {
  amountIssue,
  countErrors,
  firstIssue,
  monthsIssue,
  portfolioRowIssues,
  portfolioRowMessageList,
} from '../../settings/checks';
import { portfolioHow } from '../../settings/summary';
import { oneOf, pickValues } from '../../settings/snapshot';

const NO_PICK: FirstPick = { date: '', start: '' };
const ALLOCATIONS: readonly PortfolioAllocation[] = ['AMOUNT', 'WEIGHT'];
const METHODS: readonly PortfolioMethod[] = ['LUMP_SUM', 'DCA'];
const CADENCES: readonly DcaCadence[] = ['DAILY', 'WEEKLY', 'MONTHLY', 'MONTHS'];
const REBALANCES: readonly PortfolioRebalance[] = ['NONE', 'YEARLY', 'QUARTERLY', 'MONTHLY'];
const DEFAULTS = {
  rows: [] as unknown[],
  allocation: 'AMOUNT',
  total: FIRST_AMOUNT,
  method: 'LUMP_SUM',
  cadence: 'MONTHLY',
  months: '3',
  firstPick: NO_PICK,
  rebalance: 'NONE',
};

export const usePortfolioMode = (shared: SharedModeProps): ModeController => {
  const { symbol, supportedSymbols, period, options } = shared;
  // 고치기 전에는 줄 하나(보던 종목 · 첫 금액)
  const [edited, setEdited] = useState<PortfolioRow[] | null>(null);
  const rows = edited ?? [newRow(symbol, FIRST_AMOUNT, '100')];
  const [allocation, setAllocation] = useState<PortfolioAllocation>('AMOUNT');
  const [total, setTotal] = useState(FIRST_AMOUNT);
  const [method, setMethod] = useState<PortfolioMethod>('LUMP_SUM');
  const [cadence, setCadence] = useState<DcaCadence>('MONTHLY');
  const [months, setMonths] = useState(DEFAULTS.months);
  const [firstPick, setFirstPick] = useState<FirstPick>(NO_PICK);
  const [rebalance, setRebalance] = useState<PortfolioRebalance>('NONE');

  // 적립식 줄이 있으면 주기 · 첫 매수일 칸, 모든 줄이 적립식이면 금액 · 총액이 회당
  const dca = hasDcaRow(rows, method);
  const perRoundOnly = isAllDca(rows, method);
  const first = (dca && resolveFirstPurchase(period.start, cadence, firstPick)) || period.start;
  const { end, periodField, periodMessage } = commonFields(shared, { from: first });
  // 고른 날의 며칠(그 달에 없어 말일로 옮겨도 그대로)
  const monthDay = firstPick.date ? dayOfMonth(firstPick.date) : undefined;
  const rounds = countRounds(cadence, parseMonths(months) ?? 1, first, end, monthDay);
  const amounts = rowAmounts(rows, allocation, total);

  const rowMessages = portfolioRowIssues(rows, allocation, supportedSymbols);
  const totalLabel = perRoundOnly ? '회당 총액' : '총액';
  const totalMessage =
    allocation === 'WEIGHT' ? amountIssue({ byShares: false, amount: total, shares: '', label: totalLabel }) : null;
  const monthsMessage = dca ? monthsIssue(cadence, months) : null;
  const firstMessage = dca ? firstIssue({ first, end, cadence, monthDay: monthDay ?? dayOfMonth(first) }) : null;

  return {
    id: 'portfolio',
    label: '포트폴리오',
    description: '여러 종목을 비중대로 한 계좌에 담아 운용',
    showsChartLink: false,
    form: (
      <PortfolioForm
        assets={{
          rows,
          onRowsChange: setEdited,
          allocation,
          onAllocationChange: setAllocation,
          total,
          onTotalChange: setTotal,
          perRound: perRoundOnly,
          method,
          supportedSymbols,
          messages: rowMessages,
          totalMessage,
        }}
        period={periodField}
        method={method}
        onMethodChange={setMethod}
        cadence={
          dca ? { cadence, onCadenceChange: setCadence, months, onMonthsChange: setMonths, message: monthsMessage } : undefined
        }
        first={
          dca
            ? {
                value: first,
                picked: !!firstPick.date,
                min: period.start,
                max: end,
                onPick: (date) => setFirstPick({ date, start: period.start }),
                onFollow: () => setFirstPick(NO_PICK),
                message: firstMessage,
              }
            : undefined
        }
        rebalance={rebalance}
        onRebalanceChange={setRebalance}
      />
    ),
    summary: {
      subject: rowsSubject(rows),
      how: portfolioHow({
        lines: rows.map((row, index) => ({ strategy: rowStrategy(row, method), amount: amounts[index] })),
        cadenceLabel: cadenceText(cadence, months, first, monthDay),
        rounds,
        daily: cadence === 'DAILY',
        sell: rowsSellText(rows),
        rebalance,
      }),
    },
    errorCount: countErrors([
      periodMessage,
      totalMessage,
      ...rowMessages.flatMap(portfolioRowMessageList),
      monthsMessage,
      firstMessage,
    ]),
    saved: { rows, allocation, total, method, cadence, months, firstPick, rebalance },
    load: (saved) => {
      const next = pickValues(DEFAULTS, saved);
      const restored = savedRows(next.rows);
      setEdited(restored.length ? restored : null);
      setAllocation(oneOf(next.allocation, ALLOCATIONS, 'AMOUNT'));
      setTotal(next.total);
      setMethod(oneOf(next.method, METHODS, 'LUMP_SUM'));
      setCadence(oneOf(next.cadence, CADENCES, 'MONTHLY'));
      setMonths(next.months);
      setFirstPick(next.firstPick);
      setRebalance(oneOf(next.rebalance, REBALANCES, 'NONE'));
    },
    prepare: (ctx) => {
      const built = buildPortfolioRequest(
        {
          startDate: period.start,
          endDate: period.end,
          rows,
          allocation,
          total,
          method,
          cadence,
          months,
          firstPurchaseDate: firstPick.date,
          firstPickedStart: firstPick.start,
          rebalance,
          ...options,
        },
        ctx,
      );
      if ('error' in built) return built;
      const { request } = built;
      return {
        execute: async () => ({ ...(await backtestApi.runPortfolio(request)).data, mode: 'portfolio' as const }),
        historyType: 'STRATEGY_SIMULATION',
        params: request,
        fxMode: options.fxMode,
        failureMessage: '포트폴리오 실행에 실패했습니다.',
      };
    },
    renderResult: (result) => (result.mode === 'portfolio' ? <PortfolioResult result={result} /> : null),
  };
};
