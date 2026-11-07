import React from 'react';
import type { PerformanceMetrics } from '../../../../services/api';
import { hasValue } from '../../../../utils/gainLoss';
import { percentOrDash } from '../resultValues';

// "2024-12-26" → "24.12.26"
const shortDate = (date: string) => date.slice(2).replace(/-/g, '.');

// 비교 표의 최대 낙폭 칸(값 · 고점 → 저점 · 회복)
export const DrawdownCell: React.FC<{ metrics?: PerformanceMetrics | null }> = ({ metrics }) => (
  <span className="inline-flex flex-col items-end">
    <span className={hasValue(metrics?.mdd) && metrics.mdd < 0 ? 'text-loss' : ''}>{percentOrDash(metrics?.mdd)}</span>
    {metrics?.mddPeakDate && metrics.mddTroughDate && (
      <span className="text-xs text-tx-2 whitespace-nowrap">
        {shortDate(metrics.mddPeakDate)} → {shortDate(metrics.mddTroughDate)} ·{' '}
        {metrics.mddRecoveryDate ? `${shortDate(metrics.mddRecoveryDate)} 회복` : '회복 전'}
      </span>
    )}
  </span>
);
