import type { ReactNode } from 'react';
import { onRadioKeys } from './radioKeys';

export interface RadioOption<T extends string> {
  value: T;
  label: ReactNode;
}

interface RadioGroupProps<T extends string> {
  options: RadioOption<T>[];
  // 고른 값(null 이면 아무것도 안 고름)
  value: T | null;
  onChange: (value: T) => void;
  label?: string;
  labelledBy?: string;
  className?: string;
  optionClassName: (checked: boolean) => string;
}

// 하나만 고르는 단추 묶음(고른 항목만 Tab, 고른 값이 없으면 첫 항목)
export function RadioGroup<T extends string>({
  options,
  value,
  onChange,
  label,
  labelledBy,
  className,
  optionClassName,
}: RadioGroupProps<T>) {
  const hasValue = options.some((option) => option.value === value);
  return (
    <div role="radiogroup" aria-label={label} aria-labelledby={labelledBy} className={className} onKeyDown={onRadioKeys}>
      {options.map((option, index) => {
        const checked = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={checked}
            tabIndex={checked || (!hasValue && index === 0) ? 0 : -1}
            onClick={() => onChange(option.value)}
            className={optionClassName(checked)}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
