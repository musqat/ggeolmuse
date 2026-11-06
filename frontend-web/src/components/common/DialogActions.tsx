import React, { useRef, useState } from 'react';
import { SOLID_BUTTON } from '@/styles/buttons';

interface DialogActionsProps {
  onCancel: () => void;
  onSubmit: () => void | Promise<unknown>;
  submitLabel: string;
  // 취소 버튼 글(없으면 "취소")
  cancelLabel?: string;
  // 제출 버튼 바탕(없으면 브랜드 색)
  tone?: keyof typeof SOLID_BUTTON;
  disabled?: boolean;
  className?: string;
}

// 창 아래 취소 · 제출 버튼 줄
export const DialogActions: React.FC<DialogActionsProps> = ({
  onCancel,
  onSubmit,
  submitLabel,
  cancelLabel = '취소',
  tone,
  disabled = false,
  className = '',
}) => {
  const [pending, setPending] = useState(false);
  const inFlight = useRef(false);

  // 제출 요청이 끝날 때까지 다시 누르지 못하게 막음
  const submit = async () => {
    if (inFlight.current) return;
    inFlight.current = true;
    setPending(true);
    try {
      await onSubmit();
    } finally {
      inFlight.current = false;
      setPending(false);
    }
  };

  return (
    <div className={`flex space-x-3 ${className}`.trim()}>
      <button
        onClick={onCancel}
        className="flex-1 px-4 py-2 border border-line-strong text-tx-1 rounded-lg hover:bg-surface/50 transition-colors"
      >
        {cancelLabel}
      </button>
      <button
        onClick={() => void submit()}
        disabled={disabled || pending}
        className={`flex-1 px-4 py-2 ${tone ? SOLID_BUTTON[tone] : 'bg-brand text-brand-ink hover:bg-brand-dark'} rounded-lg disabled:opacity-50 disabled:cursor-not-allowed transition-colors`}
      >
        {submitLabel}
      </button>
    </div>
  );
};
