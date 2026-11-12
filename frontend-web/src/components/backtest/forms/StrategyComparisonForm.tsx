import React, { useId, useRef, type ReactNode } from 'react';
import { X } from 'lucide-react';
import { COMPARISON_STRATEGIES, strategyByType } from '../comparison/catalog';
import type { ComparisonStrategyType } from '../comparison/types';
import { SymbolField, type SymbolFieldProps } from '../settings/SymbolFields';
import { PeriodField, type PeriodFieldProps } from '../settings/PeriodField';
import { AmountField, type AmountFieldProps } from '../settings/AmountField';
import { SettingField } from '../settings/SettingField';
import { useMessageLink } from '../settings/messageLink';
import type { FieldMessage } from '../settings/checks';
import { FIELD_GRID } from './formLayout';

// 칩 하나의 모양(error 는 고칠 것, short 는 원금이 회차보다 모자람)
export interface ChipView {
  id: string;
  type: ComparisonStrategyType;
  name: string;
  error: string | null;
  short: boolean;
}

interface StrategyComparisonFormProps {
  symbol: SymbolFieldProps;
  period: PeriodFieldProps;
  amount: AmountFieldProps;
  chips: ChipView[];
  openId: string | null;
  onOpen: (id: string) => void;
  onRemove: (id: string) => void;
  onAdd: (type: ComparisonStrategyType) => void;
  chipsMessage?: FieldMessage | null;
  // 열린 칩의 설정 칸
  panel?: ReactNode;
}

const chipBorder = (chip: ChipView, open: boolean) => {
  if (chip.error) return 'border-danger';
  if (chip.short) return 'border-dashed border-warning';
  return open ? 'border-tx-2' : 'border-transparent';
};

// 한 종목에 단순 · 적립식 · 조건부 매수 · 이동평균 교차 · 밴드를 견줌(금액은 전략마다 원금, 설정할 칸이 있는 칩을 누르면 아래에 그 칩의 설정)
export const StrategyComparisonForm: React.FC<StrategyComparisonFormProps> = ({
  symbol,
  period,
  amount,
  chips,
  openId,
  onOpen,
  onRemove,
  onAdd,
  chipsMessage,
  panel,
}) => {
  const panelId = useId();
  const { messageId } = useMessageLink(chipsMessage);
  const hasSimple = chips.some((chip) => chip.type === 'SIMPLE');
  // 둘 미만이면 첫 더하기 단추가 고칠 칸
  const tooFew = chips.length < 2;
  const groupRef = useRef<HTMLDivElement>(null);

  // 그린 뒤 그 칩의 첫 단추(없으면 첫 더하기 단추)로 포커스
  const focusChip = (pick: (chips: HTMLElement[]) => HTMLElement | undefined) =>
    setTimeout(() => {
      const group = groupRef.current;
      if (!group) return;
      const items = [...group.querySelectorAll<HTMLElement>('[data-chip-id]')];
      (pick(items)?.querySelector<HTMLElement>('button') ?? group.querySelector<HTMLElement>('[data-add]'))?.focus();
    });

  // 뺀 자리의 다음(없으면 앞) 칩으로
  const remove = (id: string) => {
    const index = chips.findIndex((chip) => chip.id === id);
    const next = chips[index + 1]?.id ?? chips[index - 1]?.id;
    onRemove(id);
    focusChip((items) => items.find((item) => item.dataset.chipId === next));
  };

  // 더한 칩(맨 뒤)으로
  const add = (type: ComparisonStrategyType) => {
    onAdd(type);
    focusChip((items) => items[items.length - 1]);
  };

  return (
    <div className="space-y-4">
      <div className={FIELD_GRID}>
        <SymbolField {...symbol} />
        <PeriodField {...period} />
        <AmountField {...amount} />
      </div>

      <SettingField
        label="견줄 전략"
        right={<span>적립식 · 조건부 · 이동평균 교차 · 밴드는 눌러서 설정</span>}
        message={chipsMessage}
        messageId={messageId}
      >
        <div ref={groupRef} role="group" aria-label="견줄 전략" className="flex flex-wrap items-center gap-1.5">
          {chips.map((chip) => {
            // 설정할 칸이 없는 칩은 이름만
            const expandable = Boolean(strategyByType(chip.type).Fields);
            const open = expandable && chip.id === openId;
            const nameClass = `min-h-9 rounded-full py-[3px] pl-2.5 pr-1 text-[13px] text-tx-1 sm:min-h-0 ${open ? 'font-semibold' : ''}`;
            return (
              <span
                key={chip.id}
                data-chip-id={chip.id}
                className={`inline-flex items-center rounded-full border bg-elevated ${chipBorder(chip, open)}`}
              >
                {expandable ? (
                  <button
                    type="button"
                    aria-expanded={open}
                    aria-controls={panelId}
                    aria-label={chip.error ? `${chip.name} (고칠 것: ${chip.error})` : undefined}
                    data-bad={chip.error ? 'true' : undefined}
                    onClick={() => onOpen(chip.id)}
                    className={nameClass}
                  >
                    {chip.error && (
                      <span aria-hidden="true" className="mr-1 font-bold text-danger">
                        !
                      </span>
                    )}
                    {chip.name}
                  </button>
                ) : (
                  <span className={`inline-flex items-center ${nameClass}`}>{chip.name}</span>
                )}
                <button
                  type="button"
                  aria-label={`${chip.name} 빼기`}
                  onClick={() => remove(chip.id)}
                  className="inline-flex h-9 w-9 items-center justify-center rounded-full text-tx-3 hover:text-tx-1 sm:h-6 sm:w-6"
                >
                  <X className="h-3.5 w-3.5" />
                </button>
              </span>
            );
          })}
          {COMPARISON_STRATEGIES.filter(({ type }) => type !== 'SIMPLE' || !hasSimple).map(({ type, name }, index) => (
            <button
              key={type}
              type="button"
              data-add
              data-bad={tooFew && index === 0 ? 'true' : undefined}
              aria-describedby={tooFew && index === 0 && chipsMessage ? messageId : undefined}
              onClick={() => add(type)}
              className="min-h-9 px-1.5 text-[13px] text-tx-2 hover:text-tx-1 sm:min-h-0"
            >
              {`＋ ${name}`}
            </button>
          ))}
        </div>
      </SettingField>

      <div id={panelId} className={FIELD_GRID}>
        {panel}
      </div>
    </div>
  );
};
