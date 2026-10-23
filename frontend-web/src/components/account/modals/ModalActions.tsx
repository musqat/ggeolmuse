import React from 'react';
import { SOLID_BUTTON } from '@/styles/buttons';

interface ModalActionsProps {
  onCancel: () => void;
  onSubmit: () => void;
  submitLabel: string;
  // 제출 버튼 바탕(없으면 브랜드 색)
  tone?: keyof typeof SOLID_BUTTON;
  disabled?: boolean;
}

// 계좌 창 아래 취소 · 제출 버튼 줄
export const ModalActions: React.FC<ModalActionsProps> = ({ onCancel, onSubmit, submitLabel, tone, disabled = false }) => (
  <div className="flex space-x-3">
    <button
      onClick={onCancel}
      className="flex-1 px-4 py-2 border border-line-strong text-tx-1 rounded-lg hover:bg-surface/50 transition-colors"
    >
      취소
    </button>
    <button
      onClick={onSubmit}
      disabled={disabled}
      className={`flex-1 px-4 py-2 ${tone ? SOLID_BUTTON[tone] : 'bg-brand text-brand-ink hover:bg-brand-dark'} rounded-lg disabled:opacity-50 disabled:cursor-not-allowed transition-colors`}
    >
      {submitLabel}
    </button>
  </div>
);
