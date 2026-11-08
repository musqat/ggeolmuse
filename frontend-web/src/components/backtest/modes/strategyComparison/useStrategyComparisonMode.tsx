import { useRef, useState } from 'react';
import { backtestApi } from '@services/api';
import { StrategyComparisonForm } from '../../forms/StrategyComparisonForm';
import { StrategyComparisonResult } from '../../results/StrategyComparisonResult';
import { strategyByType } from '../../comparison/catalog';
import type { ComparisonStrategyType } from '../../comparison/types';
import type { ModeController, SharedModeProps } from '../types';
import { commonFields } from '../commonFields';
import { buildStrategyComparisonRequest, type StrategyChip } from './request';
import { useBacktestOptions } from '../../shared/backtestOptions';
import { hasSellRates } from '../../shared/sellRates';
import { amountIssue, countErrors, type FieldMessage } from '../../settings/checks';
import { changedOptions } from '../../settings/summary';
import type { SummaryLine } from '../../settings/SettingsSummary';
import { formatReadableWon } from '../../../../utils/format';

const LABEL = '전략마다 원금';

const newChip = (id: string, type: ComparisonStrategyType, name = strategyByType(type).name): StrategyChip => ({
  id,
  type,
  name,
  params: { ...strategyByType(type).defaultParams },
});

// 같은 종류가 있으면 가장 큰 번호 다음(지웠다 더해도 이름 안 겹침)
const nextName = (chips: StrategyChip[], type: ComparisonStrategyType) => {
  const base = strategyByType(type).name;
  const numbers = chips
    .filter((chip) => chip.type === type)
    .map((chip) => (chip.name === base ? 1 : Number(chip.name.slice(base.length + 1)) || 1));
  return numbers.length ? `${base} ${Math.max(...numbers) + 1}` : base;
};

export const useStrategyComparisonMode = (shared: SharedModeProps): ModeController => {
  const { symbol, period } = shared;
  const [investment, setInvestment] = useState('1000000');
  const [chips, setChips] = useState<StrategyChip[]>(() => [newChip('s1', 'SIMPLE'), newChip('s2', 'DCA')]);
  const [openId, setOpenId] = useState<string | null>('s2');
  const nextId = useRef(3);
  const options = useBacktestOptions();
  const { end, symbolField, periodField, symbolMessage, periodMessage } = commonFields(shared);
  const common = { startDate: period.start, endDate: end, investment };

  // 칩마다 기본값을 채운 값 · 검사 · 요약 줄
  const views = chips.map((chip) => {
    const def = strategyByType(chip.type);
    const params = { ...def.defaultParams, ...chip.params };
    const error = def.check(params, common);
    const summary = error ? null : def.summary(params, common);
    const line: SummaryLine = summary
      ? { text: `${chip.name}: ${summary.text}`, tone: summary.short ? 'warning' : 'normal' }
      : { text: `${chip.name}: ${error}`, tone: 'error' };
    return { chip, def, params, error, short: !!summary?.short, line };
  });
  const badCount = views.filter((view) => view.error).length;
  const chipsMessage: FieldMessage | null =
    chips.length < 2
      ? { kind: 'error', text: '전략을 둘 이상' }
      : badCount > 0
        ? { kind: 'error', text: `고칠 칩 ${badCount}개(! 표시)는 눌러서 고치기` }
        : views.some((view) => view.short)
          ? { kind: 'warning', text: '점선 칩은 원금이 회차보다 모자라 뒤 회차를 못 삼' }
          : null;
  const amountMessage = amountIssue({ byShares: false, amount: investment, shares: '', label: LABEL });
  const open = views.find((view) => view.chip.id === openId);
  const conditionalSells = views.some(
    (view) => view.chip.type === 'CONDITIONAL_PURCHASE' && hasSellRates(view.params.takeProfit, view.params.stopLoss),
  );

  const addChip = (type: ComparisonStrategyType) => {
    const id = `s${nextId.current++}`;
    setChips([...chips, newChip(id, type, nextName(chips, type))]);
    setOpenId(id);
  };

  // 열린 칩을 빼면 그 자리의 칩을 엶
  const removeChip = (id: string) => {
    const index = chips.findIndex((chip) => chip.id === id);
    const rest = chips.filter((chip) => chip.id !== id);
    setChips(rest);
    if (openId === id) setOpenId(rest[Math.min(index, rest.length - 1)]?.id ?? null);
  };

  const changeParam = (id: string, key: string, value: string) =>
    setChips((prev) => prev.map((chip) => (chip.id === id ? { ...chip, params: { ...chip.params, [key]: value } } : chip)));

  const OpenFields = open?.def.Fields;

  return {
    id: 'compare-strategies',
    label: '전략 비교',
    showsChartLink: false,
    form: (
      <StrategyComparisonForm
        symbol={symbolField}
        period={periodField}
        amount={{ label: LABEL, amount: investment, onAmountChange: setInvestment, message: amountMessage }}
        chips={views.map(({ chip, error, short }) => ({ id: chip.id, type: chip.type, name: chip.name, error, short }))}
        openId={openId}
        onOpen={setOpenId}
        onRemove={removeChip}
        onAdd={addChip}
        chipsMessage={chipsMessage}
        panel={
          open &&
          OpenFields && (
            <OpenFields
              key={open.chip.id}
              name={open.chip.name}
              params={open.params}
              onChange={(key, value) => changeParam(open.chip.id, key, value)}
              common={common}
            />
          )
        }
        options={options}
        reinvestNote={conditionalSells ? '익절 · 손절이 있는 조건부 매수는 재투자 안 함' : undefined}
      />
    ),
    summary: {
      subject: symbol,
      how: `전략마다 원금 ${formatReadableWon(parseFloat(investment))}`,
      changed: changedOptions(options, false),
      lines: views.map((view) => view.line),
    },
    errorCount: countErrors([symbolMessage, periodMessage, amountMessage]) + (chips.length < 2 ? 1 : 0) + badCount,
    prepare: (ctx) => {
      const built = buildStrategyComparisonRequest(
        {
          symbol,
          startDate: period.start,
          endDate: period.end,
          investment,
          chips: views.map((view) => ({ ...view.chip, params: view.params })),
          ...options,
        },
        ctx,
      );
      if ('error' in built) return built;
      const { request } = built;
      return {
        execute: async () => ({
          ...(await backtestApi.compareStrategies(request)).data,
          mode: 'compare-strategies' as const,
        }),
        historyType: 'COMPARISON',
        params: request,
        fxMode: options.fxMode,
        failureMessage: '전략 비교 실행에 실패했습니다.',
      };
    },
    renderResult: (result) =>
      result.mode === 'compare-strategies' ? <StrategyComparisonResult result={result} /> : null,
  };
};
