import { useId } from 'react';
import type { DcaCadence } from '../shared/dcaSchedule';
import { SettingField } from './SettingField';
import { useMessageLink } from './messageLink';
import { onRadioKeys } from './radioKeys';
import { digitsOnly } from './inputText';
import type { FieldMessage } from './checks';
import { PILL } from './styles';

const FIXED: { value: DcaCadence; label: string }[] = [
  { value: 'DAILY', label: '매일' },
  { value: 'WEEKLY', label: '매주' },
  { value: 'MONTHLY', label: '매달' },
];

export interface CadenceFieldProps {
  // 칩 설정이면 "적립식 2 주기"
  label?: string;
  cadence: DcaCadence;
  onCadenceChange: (cadence: DcaCadence) => void;
  months: string;
  onMonthsChange: (months: string) => void;
  message?: FieldMessage | null;
}

// 적립식 주기 칸: 매일 · 매주 · 매달 · [N]개월마다를 하나만 고름(개월 칸에 쓰면 N개월마다)
export const CadenceField = ({ label = '언제', cadence, onCadenceChange, months, onMonthsChange, message }: CadenceFieldProps) => {
  const labelId = useId();
  const { messageId, invalid, describedBy } = useMessageLink(message);
  const radio = (value: DcaCadence) => ({
    role: 'radio' as const,
    'aria-checked': cadence === value,
    tabIndex: cadence === value ? 0 : -1,
    onClick: () => onCadenceChange(value),
    onKeyDown: onRadioKeys,
  });

  return (
    <SettingField label={label} labelId={labelId} message={message} messageId={messageId}>
      <div role="radiogroup" aria-labelledby={labelId} className="flex flex-wrap items-center gap-1.5">
        {FIXED.map(({ value, label: text }) => (
          <button key={value} type="button" {...radio(value)} className={PILL(cadence === value)}>
            {text}
          </button>
        ))}
        {/* 빈 곳 누르기는 마우스 편의(키보드는 안의 개월마다 라디오)라 감싼 칸 자체는 역할 없음 */}
        <span
          role="presentation"
          className={PILL(cadence === 'MONTHS')}
          onClick={(e) => {
            if (e.target === e.currentTarget) onCadenceChange('MONTHS');
          }}
        >
          <input
            className="w-[3ch] border-0 border-b border-current bg-transparent p-0 text-center text-base outline-none focus-visible:border-b-2 focus-visible:border-[rgb(var(--focus))] sm:text-[13px]"
            inputMode="numeric"
            aria-label="몇 개월마다"
            aria-invalid={invalid || undefined}
            aria-describedby={describedBy}
            value={months}
            onChange={(e) => {
              onMonthsChange(digitsOnly(e.target.value));
              onCadenceChange('MONTHS');
            }}
          />
          <button type="button" {...radio('MONTHS')} className="bg-transparent p-0">
            개월마다
          </button>
        </span>
      </div>
    </SettingField>
  );
};
