import React from 'react';

const FOCUS_COLOR = {
  default: 'focus:ring-brand focus:border-brand',
  danger: 'focus:ring-danger focus:border-danger',
};

interface DialogFieldProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'className'> {
  id: string;
  label: React.ReactNode;
  hint?: string;
  tone?: keyof typeof FOCUS_COLOR;
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
