import React from 'react';

interface StatCardProps {
  label: string;
  // 라벨 뒤 기준(누적 매수액 기준 등)
  note?: string;
  value: React.ReactNode;
  sub?: React.ReactNode;
  valueClassName?: string;
  testId?: string;
  // 칸 너비 등 바깥 배치
  className?: string;
  // 휴대폰 두 칸 배치용(여백 · 값 글자를 줄이고 긴 값은 칸 안에서 줄바꿈)
  compact?: boolean;
}

// 결과 위쪽 카드(라벨 · 큰 값 · 보조 줄)
export const StatCard: React.FC<StatCardProps> = ({
  label,
  note,
  value,
  sub,
  valueClassName = 'text-tx-1',
  testId,
  className = '',
  compact = false,
}) => (
  <div className={`bg-surface rounded-xl shadow-sm border border-line/50 ${compact ? 'min-w-0 p-4 sm:p-5' : 'p-5'} ${className}`}>
    <p className="text-sm font-medium text-tx-2">
      {label}
      {note && <span className="font-normal"> · {note}</span>}
    </p>
    <p
      className={`${compact ? 'text-xl sm:text-2xl break-words' : 'text-2xl'} font-bold mt-1 ${valueClassName}`}
      data-testid={testId}
    >
      {value}
    </p>
    {sub && <div className="text-xs text-tx-2 mt-1 space-y-0.5">{sub}</div>}
  </div>
);
