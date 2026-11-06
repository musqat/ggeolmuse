import React from 'react';

export type MetricCurrency = 'KRW' | 'USD';

const OPTIONS: { value: MetricCurrency; label: string }[] = [
  { value: 'KRW', label: '원화 기준' },
  { value: 'USD', label: '달러 기준' },
];

interface CurrencyToggleProps {
  value: MetricCurrency;
  onChange: (value: MetricCurrency) => void;
}

// 지표 기준 통화 고르기(원화 · 달러)
export const CurrencyToggle: React.FC<CurrencyToggleProps> = ({ value, onChange }) => (
  <div role="group" aria-label="지표 기준 통화" className="inline-flex rounded-btn border border-line/50 bg-surface p-0.5">
    {OPTIONS.map((option) => (
      <button
        key={option.value}
        type="button"
        aria-pressed={value === option.value}
        onClick={() => onChange(option.value)}
        className={`px-3 py-1 text-sm rounded-btn ${
          value === option.value ? 'bg-elevated text-tx-1 font-medium' : 'text-tx-2 hover:text-tx-1'
        }`}
      >
        {option.label}
      </button>
    ))}
  </div>
);
