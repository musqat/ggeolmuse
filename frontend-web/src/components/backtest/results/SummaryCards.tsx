import React from 'react';
import type { PerformanceMetrics, StrategyResponse } from '../../../services/api';
import { gainLossClass, hasValue } from '../../../utils/gainLoss';
import { formatKrw, formatPercent, formatSigned, formatNumber } from '../../../utils/format';
import { StatCard } from './StatCard';
import { cashKrw } from '../shared/backtestDisplay';
import { percentOrDash } from './resultValues';

type SummaryValues = Pick<
  StrategyResponse,
  'totalAssetKrw' | 'currentValueKrw' | 'totalReturnKrw' | 'totalReturnPercent'
>;

interface SummaryCardsProps {
  result: SummaryValues;
  // 투자금 라벨(단순은 초기 투자금, 적립식 · 조건부는 총 투자금)
  investedLabel: string;
  invested: number | null | undefined;
  // 고른 통화 · 다른 통화의 지표와 그 통화 이름
  metrics?: PerformanceMetrics | null;
  other?: PerformanceMetrics | null;
  otherLabel: string;
  // 응답에 지표 칸이 없음
  noMetrics?: boolean;
  // 지표는 달러 기준, 총 수익률은 원화 기준
  totalInKrw?: boolean;
  timeWeighted?: boolean;
  returnRateTestId?: string;
}

const Nowrap: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <span className="whitespace-nowrap">{children}</span>
);

// 최대 낙폭의 고점 → 저점과 회복(날짜는 줄이 바뀌어도 한 덩어리)
const DrawdownSpan: React.FC<{ metrics?: PerformanceMetrics | null; noMetrics?: boolean }> = ({ metrics, noMetrics }) => {
  if (noMetrics) return <>지표 없음</>;
  if (!hasValue(metrics?.mdd)) return <>일별 시세가 없어 내지 않음</>;
  if (!metrics?.mddPeakDate || !metrics.mddTroughDate) return <>기간 중 하락 없음</>;
  return (
    <>
      <Nowrap>{metrics.mddPeakDate}</Nowrap> → <Nowrap>{metrics.mddTroughDate}</Nowrap> ·{' '}
      <Nowrap>{metrics.mddRecoveryDate ? `${metrics.mddRecoveryDate} 회복` : '아직 회복 전'}</Nowrap>
    </>
  );
};

// 결과 위쪽 카드 한 줄: 총 수익률 · 연평균 수익률 · 최대 낙폭 · 평가 가치
export const SummaryCards: React.FC<SummaryCardsProps> = ({
  result,
  investedLabel,
  invested,
  metrics,
  other,
  otherLabel,
  noMetrics,
  totalInKrw,
  timeWeighted,
  returnRateTestId,
}) => (
  <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
    <StatCard
      label="총 수익률"
      value={formatSigned(result.totalReturnPercent, formatPercent)}
      valueClassName={gainLossClass(result.totalReturnPercent)}
      sub={
        <p>
          {totalInKrw && '원화 기준 · '}{investedLabel} ₩{formatNumber(invested)} 대비{' '}
          <span className="whitespace-nowrap">{formatSigned(result.totalReturnKrw, formatKrw)}</span>
        </p>
      }
      testId={returnRateTestId}
    />

    <StatCard
      label="연평균 수익률"
      value={hasValue(metrics?.cagr) ? formatSigned(metrics?.cagr, formatPercent) : '—'}
      valueClassName={hasValue(metrics?.cagr) ? gainLossClass(metrics?.cagr) : 'text-tx-3'}
      sub={
        <p>
          {hasValue(metrics?.cagr)
            ? <>{timeWeighted && '입금을 뺀 시간가중 · '}{otherLabel} 기준 {formatSigned(other?.cagr, formatPercent)}</>
            : noMetrics ? '지표 없음' : '1년 미만이면 내지 않음'}
        </p>
      }
    />

    <StatCard
      label="최대 낙폭"
      value={percentOrDash(metrics?.mdd)}
      valueClassName={hasValue(metrics?.mdd) && metrics.mdd < 0 ? 'text-loss' : hasValue(metrics?.mdd) ? 'text-tx-1' : 'text-tx-3'}
      sub={
        <>
          <p>
            <DrawdownSpan metrics={metrics} noMetrics={noMetrics} />
          </p>
          {!noMetrics && (
            <p>
              {timeWeighted && '입금을 뺀 시간가중 · '}{otherLabel} 기준 {percentOrDash(other?.mdd)}
            </p>
          )}
        </>
      }
    />

    <StatCard
      label="평가 가치"
      value={<>₩{formatNumber(result.totalAssetKrw)}</>}
      sub={
        <p>
          주식: ₩{formatNumber(result.currentValueKrw)}, 현금: ₩
          {formatNumber(cashKrw(result))}
        </p>
      }
    />
  </div>
);
