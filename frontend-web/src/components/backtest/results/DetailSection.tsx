import React from 'react';

interface DetailSectionProps {
  title: string;
  children: React.ReactNode;
  className?: string;
}

// 상세 칸 안의 제목과 줄 묶음
export const DetailSection: React.FC<DetailSectionProps> = ({ title, children, className }) => (
  <div className={className}>
    <h3 className="text-base font-semibold text-tx-1 mb-3">{title}</h3>
    <div className="space-y-1">{children}</div>
  </div>
);

interface DetailRowProps {
  label: string;
  value: React.ReactNode;
  valueClassName?: string;
  last?: boolean;
}

export const DetailRow: React.FC<DetailRowProps> = ({ label, value, valueClassName = 'font-medium text-tx-1', last = false }) => (
  <div className={`flex justify-between gap-3 py-2${last ? '' : ' border-b border-line/50'}`}>
    <span className="text-tx-2">{label}</span>
    <span className={`text-right ${valueClassName}`}>{value}</span>
  </div>
);
