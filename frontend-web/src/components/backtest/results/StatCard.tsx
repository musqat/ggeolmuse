import React from 'react';
import type { LucideIcon } from 'lucide-react';

interface StatCardProps {
  label: string;
  value: React.ReactNode;
  icon: LucideIcon;
  sub?: React.ReactNode;
  valueClassName?: string;
  iconBoxClassName?: string;
  iconClassName?: string;
  testId?: string;
}

export const StatCard: React.FC<StatCardProps> = ({
  label,
  value,
  icon: Icon,
  sub,
  valueClassName = 'text-tx-1',
  iconBoxClassName = 'bg-brand-bg',
  iconClassName = 'text-brand',
  testId,
}) => (
  <div className="bg-surface rounded-xl shadow-sm p-6 border border-line/50">
    <div className="flex items-center justify-between">
      <div>
        <p className="text-sm font-medium text-tx-2">{label}</p>
        <p className={`text-2xl font-bold mt-1 ${valueClassName}`} data-testid={testId}>
          {value}
        </p>
        {sub && <p className="text-xs text-tx-2 mt-1">{sub}</p>}
      </div>
      <div className={`${iconBoxClassName} p-3 rounded-lg`}>
        <Icon className={`w-6 h-6 ${iconClassName}`} />
      </div>
    </div>
  </div>
);
