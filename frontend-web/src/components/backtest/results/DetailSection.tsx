import React from 'react';
import type { LucideIcon } from 'lucide-react';

interface DetailSectionProps {
  title: string;
  icon: LucideIcon;
  children: React.ReactNode;
  iconClassName?: string;
  borderClassName?: string;
  className?: string;
}

export const DetailSection: React.FC<DetailSectionProps> = ({
  title,
  icon: Icon,
  children,
  iconClassName = 'text-brand',
  borderClassName = 'border-line/50',
  className,
}) => (
  <div className={`bg-surface rounded-xl shadow-sm border ${borderClassName} p-6${className ? ` ${className}` : ''}`}>
    <h3 className="text-lg font-semibold text-tx-1 mb-4 flex items-center">
      <Icon className={`w-5 h-5 mr-2 ${iconClassName}`} />
      {title}
    </h3>
    <div className="space-y-3">{children}</div>
  </div>
);

interface DetailRowProps {
  label: string;
  value: React.ReactNode;
  valueClassName?: string;
  dividerClassName?: string;
  last?: boolean;
}

export const DetailRow: React.FC<DetailRowProps> = ({
  label,
  value,
  valueClassName = 'font-medium text-tx-1',
  dividerClassName = 'border-line/50',
  last = false,
}) => (
  <div className={`flex justify-between py-2${last ? '' : ` border-b ${dividerClassName}`}`}>
    <span className="text-tx-2">{label}</span>
    <span className={valueClassName}>{value}</span>
  </div>
);
