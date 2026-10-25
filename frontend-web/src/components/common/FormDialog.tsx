import React from 'react';
import { DialogActions } from './DialogActions';
import { LAYER } from '@/styles/layers';

type DialogTone = 'default' | 'danger';

const TITLE_COLOR: Record<DialogTone, string> = {
  default: 'text-tx-1',
  danger: 'text-danger',
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
  <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center" style={{ zIndex: LAYER.modal }}>
    <div className="bg-surface rounded-xl p-6 w-full max-w-md mx-4">
      <h3 className={`text-lg font-semibold ${TITLE_COLOR[tone]} mb-4`}>{title}</h3>

      <div className="space-y-4">{children}</div>

      <DialogActions
        className="mt-6"
        onCancel={onCancel}
        onSubmit={onConfirm}
        submitLabel={confirmLabel}
        tone={tone === 'danger' ? 'danger' : undefined}
        disabled={confirmDisabled}
      />
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
