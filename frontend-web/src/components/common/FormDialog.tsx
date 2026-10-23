import React from 'react';

type DialogTone = 'default' | 'danger';

const TITLE_COLOR: Record<DialogTone, string> = {
  default: 'text-tx-1',
  danger: 'text-danger',
};
const CONFIRM_COLOR: Record<DialogTone, string> = {
  default: 'bg-brand text-brand-ink hover:bg-brand-dark',
  danger: 'bg-red-600 text-white hover:bg-red-700',
};
const FOCUS_COLOR: Record<DialogTone, string> = {
  default: 'focus:ring-brand focus:border-brand',
  danger: 'focus:ring-danger focus:border-danger',
};

interface FormDialogProps {
  title: string;
  tone?: DialogTone;
  confirmLabel: string;
  confirmDisabled: boolean;
  onConfirm: () => void;
  onCancel: () => void;
  children: React.ReactNode;
}

// 제목 · 입력 칸 · 취소/확인 줄로 된 창
export const FormDialog: React.FC<FormDialogProps> = ({
  title,
  tone = 'default',
  confirmLabel,
  confirmDisabled,
  onConfirm,
  onCancel,
  children,
}) => (
  <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-[9999]">
    <div className="bg-surface rounded-xl p-6 w-full max-w-md mx-4">
      <h3 className={`text-lg font-semibold ${TITLE_COLOR[tone]} mb-4`}>{title}</h3>

      <div className="space-y-4">{children}</div>

      <div className="flex space-x-3 mt-6">
        <button
          onClick={onCancel}
          className="flex-1 px-4 py-2 border border-line-strong text-tx-1 rounded-lg hover:bg-surface/50 transition-colors"
        >
          취소
        </button>
        <button
          onClick={onConfirm}
          disabled={confirmDisabled}
          className={`flex-1 px-4 py-2 ${CONFIRM_COLOR[tone]} rounded-lg disabled:opacity-50 disabled:cursor-not-allowed transition-colors`}
        >
          {confirmLabel}
        </button>
      </div>
    </div>
  </div>
);

interface DialogFieldProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'className'> {
  id: string;
  label: React.ReactNode;
  hint?: string;
  tone?: DialogTone;
}

// 창 안의 이름표 · 입력 칸 · 도움말
export const DialogField: React.FC<DialogFieldProps> = ({ id, label, hint, tone = 'default', ...input }) => (
  <div>
    <label htmlFor={id} className="block text-sm font-medium text-tx-1 mb-2">
      {label}
    </label>
    <input
      id={id}
      {...input}
      className={`w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 ${FOCUS_COLOR[tone]}`}
    />
    {hint && <p className="text-xs text-tx-2 mt-1">{hint}</p>}
  </div>
);
