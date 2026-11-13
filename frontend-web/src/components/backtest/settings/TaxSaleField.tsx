import { useId, useLayoutEffect, useRef } from 'react';
import type { TaxSaleControl, TaxSaleOrder } from '../shared/taxSale';
import { SettingField } from './SettingField';
import { RadioGroup } from './radio';
import { PILL, VALUE_ROW } from './styles';

// 판매 차례 위 · 아래 단추(휴대폰은 누르기 쉬운 크기)
const MOVE =
  'inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-[11px] text-tx-2 hover:bg-elevated hover:text-tx-1 ' +
  'disabled:opacity-30 disabled:hover:bg-transparent disabled:hover:text-tx-2 sm:h-7 sm:w-7';

// 먼저 팔 종목부터 목록: 줄마다 차례 · 종목 · ▲ · ▼(맨 위 ▲ · 맨 아래 ▼ 막음), 옮긴 뒤 옮긴 종목의 같은 쪽 단추에 포커스(막혔으면 다른 쪽)
const SaleOrderList = ({ order }: { order: TaxSaleOrder }) => {
  const labelId = useId();
  const listRef = useRef<HTMLOListElement>(null);
  const focusAfter = useRef<{ position: number; step: -1 | 1 } | null>(null);

  useLayoutEffect(() => {
    const target = focusAfter.current;
    if (!target) return;
    focusAfter.current = null;
    const [up, down] = Array.from(listRef.current?.children[target.position]?.querySelectorAll('button') ?? []);
    const same = target.step < 0 ? up : down;
    (same?.disabled ? (target.step < 0 ? down : up) : same)?.focus();
  });

  const move = (position: number, step: -1 | 1) => {
    focusAfter.current = { position: position + step, step };
    order.onMove(position, step);
  };
  const last = order.names.length - 1;

  return (
    <div className="mt-1.5">
      <p id={labelId} className="text-xs text-tx-2">
        먼저 팔 종목부터
      </p>
      <ol ref={listRef} aria-labelledby={labelId}>
        {order.names.map((name, position) => (
          <li key={position} className="flex min-w-0 items-center gap-2 text-base text-tx-1 sm:text-[15px]">
            <span className="w-4 shrink-0 text-right text-[13px] text-tx-2">{position + 1}</span>
            <span className="min-w-0 flex-1 truncate">{name}</span>
            <button
              type="button"
              aria-label={`${name} 위로`}
              disabled={position === 0}
              onClick={() => move(position, -1)}
              className={MOVE}
            >
              <span aria-hidden>▲</span>
            </button>
            <button
              type="button"
              aria-label={`${name} 아래로`}
              disabled={position === last}
              onClick={() => move(position, 1)}
              className={MOVE}
            >
              <span aria-hidden>▼</span>
            </button>
          </li>
        ))}
      </ol>
    </div>
  );
};

// 세부 설정 "현금이 모자라면" 칸: 단일 종목은 칩 하나 고르기(줄바꿈), 포트폴리오는 드롭다운과 정한 순서로 팔 때 그 아래 먼저 팔 종목부터 목록
export const TaxSaleField = ({ control }: { control: TaxSaleControl }) => {
  const labelId = useId();
  const { order } = control;
  return (
    <SettingField label="현금이 모자라면" labelId={labelId}>
      {order ? (
        <>
          <div className={VALUE_ROW}>
            <select
              className="input-inline cursor-pointer"
              aria-labelledby={labelId}
              value={control.value}
              onChange={(e) => control.onChange(e.target.value)}
            >
              {control.options.map(({ value, label }) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>
          {control.value === 'CUSTOM_ORDER' && <SaleOrderList order={order} />}
        </>
      ) : (
        <RadioGroup
          labelledBy={labelId}
          options={control.options.map(({ value, label }) => ({ value, label }))}
          value={control.value}
          onChange={control.onChange}
          className="flex flex-wrap items-center gap-1.5"
          optionClassName={PILL}
        />
      )}
    </SettingField>
  );
};
