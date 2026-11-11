import { useLayoutEffect, useRef } from 'react';
import { ClearButton, SettingField } from './SettingField';
import { useMessageLink } from './messageLink';
import { oneDecimal } from './inputText';
import { isError, type FieldMessage } from './checks';
import { MAX_TAKE_PROFIT_STEPS, type TakeProfitStep } from '../shared/partialTakeProfit';
import { LEAD, UNIT, VALUE_ROW } from './styles';

export interface PartialTakeProfitFieldProps {
  steps: TakeProfitStep[];
  onStepsChange: (steps: TakeProfitStep[]) => void;
  messages: { gain: FieldMessage | null; sell: FieldMessage | null }[];
}

// 넓은 화면에서만 보이는 풀이 글(휴대폰은 줄마다 두 칸 + 지우기 단추)
const WIDE_ONLY = `${LEAD} hidden md:inline`;

// 부분 익절 칸: 이름 줄 오른쪽 "평균 단가 대비", 단계마다 "+[20]% 오르면 · 보유의 [25]% 팔기 [지우기]", 아래 "＋ 단계 더하기"(5단계까지, 오류 글은 앞 단계 것 한 줄)
export const PartialTakeProfitField = ({ steps, onStepsChange, messages }: PartialTakeProfitFieldProps) => {
  const message = messages.flatMap((step) => [step.gain, step.sell]).find(Boolean) ?? null;
  const { messageId, describedBy } = useMessageLink(message);
  const listRef = useRef<HTMLDivElement>(null);
  // 다시 그린 뒤 포커스할 단계(그 단계의 수익률 칸, 없으면 더하기 단추)
  const focusAfter = useRef<number | null>(null);

  useLayoutEffect(() => {
    const index = focusAfter.current;
    if (index === null) return;
    focusAfter.current = null;
    const list = listRef.current;
    const gain = list?.querySelectorAll<HTMLElement>('[data-step]')[index]?.querySelector('input');
    (gain ?? list?.querySelector<HTMLElement>('[data-add]'))?.focus();
  });

  const change = (index: number, key: keyof TakeProfitStep, value: string) =>
    onStepsChange(steps.map((step, i) => (i === index ? { ...step, [key]: value } : step)));
  const add = () => {
    focusAfter.current = steps.length;
    onStepsChange([...steps, { gain: '', sell: '' }]);
  };
  // 지운 자리의 단계(없으면 앞 단계)로
  const remove = (index: number) => {
    focusAfter.current = index < steps.length - 1 ? index : index - 1;
    onStepsChange(steps.filter((_, i) => i !== index));
  };

  const input = (index: number, key: keyof TakeProfitStep, name: string) => {
    const own = messages[index]?.[key] ?? null;
    return (
      <input
        className="input-inline"
        inputMode="decimal"
        aria-label={name}
        aria-invalid={isError(own) || undefined}
        aria-describedby={own && own === message ? describedBy : undefined}
        value={steps[index][key]}
        onChange={(e) => change(index, key, oneDecimal(e.target.value))}
      />
    );
  };

  return (
    <SettingField label="부분 익절" right={<span>평균 단가 대비</span>} message={message} messageId={messageId}>
      <div ref={listRef}>
        {steps.map((_, index) => (
          <div key={index} data-step className={VALUE_ROW}>
            <span className={UNIT}>+</span>
            {input(index, 'gain', `${index + 1}단계 수익률(평균 단가 대비 퍼센트)`)}
            <span className={UNIT}>%</span>
            <span className={WIDE_ONLY}>오르면</span>
            <span className={`${LEAD} ml-1`}>· 보유의</span>
            {input(index, 'sell', `${index + 1}단계 팔 비율(보유 대비 퍼센트)`)}
            <span className={UNIT}>%</span>
            <span className={WIDE_ONLY}>팔기</span>
            <span className="ml-auto">
              <ClearButton label={`${index + 1}단계 지우기`} onClick={() => remove(index)} />
            </span>
          </div>
        ))}
        <div className="flex flex-wrap items-center gap-x-2">
          {steps.length === 0 && <span className="text-base text-tx-3 sm:text-[15px]">안 함</span>}
          {steps.length < MAX_TAKE_PROFIT_STEPS ? (
            <button
              type="button"
              data-add
              onClick={add}
              className="min-h-9 px-1.5 text-[13px] text-tx-2 hover:text-tx-1 sm:min-h-0"
            >
              ＋ 단계 더하기
            </button>
          ) : (
            <span className="text-[13px] text-tx-3">최대 {MAX_TAKE_PROFIT_STEPS}단계</span>
          )}
        </div>
      </div>
    </SettingField>
  );
};
