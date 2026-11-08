import React from 'react';

interface StatCardProps {
  label: string;
  value: React.ReactNode;
  sub?: React.ReactNode;
  valueClassName?: string;
  testId?: string;
  // 칸 너비 등 바깥 배치
  className?: string;
}

// 결과 위쪽 카드(라벨 · 큰 값 · 보조 줄)
export const StatCard: React.FC<StatCardProps> = ({ label, value, sub, valueClassName = 'text-tx-1', testId, className = '' }) => (
  <div className={`bg-surface rounded-xl shadow-sm p-5 border border-line/50 ${className}`}>
    <p className="text-sm font-medium text-tx-2">{label}</p>
    <p className={`text-2xl font-bold mt-1 ${valueClassName}`} data-testid={testId}>
      {value}
    </p>
    {sub && <div className="text-xs text-tx-2 mt-1 space-y-0.5">{sub}</div>}
  </div>
);
