import React from 'react';
import { TrendingDown, Waves, Scale } from 'lucide-react';
import type { PerformanceMetrics } from '../../../services/api';
import { hasValue } from '../../../utils/gainLoss';
import { formatPercent } from '../../../utils/format';
import { StatCard } from './StatCard';

interface RiskCardsProps {
  metrics?: PerformanceMetrics | null;
  other?: PerformanceMetrics | null;
  otherLabel: string;
}

const percentOrDash = (value: number | null | undefined) => (hasValue(value) ? formatPercent(value) : '—');
const ratioOrDash = (value: number | null | undefined) => (hasValue(value) ? value.toFixed(2) : '—');

const Nowrap: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <span className="whitespace-nowrap">{children}</span>
);

// 최대 낙폭의 고점 → 저점과 회복(날짜는 줄이 바뀌어도 한 덩어리)
const DrawdownSpan: React.FC<{ metrics?: PerformanceMetrics | null }> = ({ metrics }) => {
  if (!hasValue(metrics?.mdd)) return <>일별 시세가 없어 내지 않음</>;
  if (!metrics?.mddPeakDate || !metrics.mddTroughDate) return <>기간 중 하락 없음</>;
  return (
    <>
      <Nowrap>{metrics.mddPeakDate}</Nowrap> → <Nowrap>{metrics.mddTroughDate}</Nowrap> ·{' '}
      <Nowrap>{metrics.mddRecoveryDate ? `${metrics.mddRecoveryDate} 회복` : '아직 회복 전'}</Nowrap>
    </>
  );
};

// 결과 위쪽 위험 · 효율 줄: 최대 낙폭 · 변동성 · 샤프 비율
export const RiskCards: React.FC<RiskCardsProps> = ({ metrics, other, otherLabel }) => (
  <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
    <StatCard
      label="최대 낙폭"
      value={percentOrDash(metrics?.mdd)}
      valueClassName={hasValue(metrics?.mdd) && metrics.mdd < 0 ? 'text-loss' : 'text-tx-1'}
      sub={
        <>
          <span className="block">
            <DrawdownSpan metrics={metrics} />
          </span>
          <span className="block">{otherLabel} 기준 {percentOrDash(other?.mdd)}</span>
        </>
      }
      icon={TrendingDown}
      iconBoxClassName="bg-loss/15"
      iconClassName="text-loss"
    />

    <StatCard
      label="변동성(연)"
      value={percentOrDash(metrics?.volatility)}
      sub={<>{otherLabel} 기준 {percentOrDash(other?.volatility)}</>}
      icon={Waves}
    />

    <StatCard
      label="샤프 비율"
      value={ratioOrDash(metrics?.sharpe)}
      sub={<>무위험 연 3% · {otherLabel} 기준 {ratioOrDash(other?.sharpe)}</>}
      icon={Scale}
    />
  </div>
);
