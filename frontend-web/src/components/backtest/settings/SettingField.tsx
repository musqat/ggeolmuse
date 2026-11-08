import type { MouseEvent, ReactNode } from 'react';
import { X } from 'lucide-react';
import { isError, type FieldMessage } from './checks';

interface SettingFieldProps {
  label: ReactNode;
  labelId?: string;
  // 이름 줄 오른쪽(작은 고르기 · 비우기 단추 · 개수)
  right?: ReactNode;
  message?: FieldMessage | null;
  messageId?: string;
  className?: string;
  children: ReactNode;
}

// 백테스트 설정 칸 상자: 이름 줄 · 값 · 오류나 경고 한 줄(높이는 내용만큼)
export const SettingField = ({ label, labelId, right, message, messageId, className = '', children }: SettingFieldProps) => {
  // 상자 빈 곳을 누르면 값 줄의 첫 입력으로
  const focusValue = (event: MouseEvent<HTMLDivElement>) => {
    if ((event.target as HTMLElement).closest('input, button, a, [role="listbox"], [role="radiogroup"]')) return;
    event.currentTarget.querySelector<HTMLInputElement>('[data-field-value] input')?.focus();
  };
  const tone = message?.kind === 'error' ? 'text-danger' : 'text-warning';

  return (
    <div
      onClick={focusValue}
      className={`relative min-w-0 rounded-[10px] border bg-canvas/45 px-3.5 py-2 transition-colors focus-within:border-[rgb(var(--focus))] focus-within:shadow-[0_0_0_1px_rgb(var(--focus))] motion-reduce:transition-none ${
        isError(message) ? 'border-danger' : 'border-line hover:border-line-strong'
      } ${className}`.trim()}
    >
      <div className="mb-0.5 flex min-h-6 flex-wrap items-center justify-between gap-x-2 gap-y-1 text-xs text-tx-2">
        <span id={labelId}>{label}</span>
        {right}
      </div>
      <div data-field-value>{children}</div>
      {message && (
        <p className="mt-0.5 text-xs">
          <span id={messageId} className={tone}>
            {message.text}
          </span>
          {message.action && (
            <button
              type="button"
              onClick={message.action.onClick}
              className={`ml-1.5 min-h-9 rounded-full border border-current px-2 sm:min-h-0 sm:py-px ${tone}`}
            >
              {message.action.label}
            </button>
          )}
        </p>
      )}
    </div>
  );
};

// 칸 비우기 단추
export const ClearButton = ({ label, onClick }: { label: string; onClick: () => void }) => (
  <button
    type="button"
    aria-label={label}
    onClick={onClick}
    className="-my-1 inline-flex h-9 w-9 items-center justify-center rounded-full text-tx-3 hover:bg-elevated hover:text-tx-1 sm:my-0 sm:h-6 sm:w-6"
  >
    <X className="h-3.5 w-3.5" />
  </button>
);
