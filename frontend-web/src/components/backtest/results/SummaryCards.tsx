import React from 'react';
import { TrendingUp, TrendingDown, DollarSign, CalendarClock } from 'lucide-react';
import type { PerformanceMetrics, StrategyResponse } from '../../../services/api';
import { gainLossBoxClass, gainLossClass, gainLossTone, hasValue } from '../../../utils/gainLoss';
import { formatKrw, formatPercent, formatSigned, formatNumber } from '../../../utils/format';
import { StatCard } from './StatCard';
import { cashKrw } from '../shared/backtestDisplay';

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

// 결과 위쪽 수익 줄: 총 수익률 · 연평균 수익률 · 평가 가치
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
  <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
    <StatCard
      label="총 수익률"
      value={formatSigned(result.totalReturnPercent, formatPercent)}
      valueClassName={gainLossClass(result.totalReturnPercent)}
      sub={
        <>
          {totalInKrw && '원화 기준 · '}{investedLabel} ₩{formatNumber(invested)} 대비{' '}
          <span className="whitespace-nowrap">{formatSigned(result.totalReturnKrw, formatKrw)}</span>
        </>
      }
      icon={gainLossTone(result.totalReturnPercent) === 'loss' ? TrendingDown : TrendingUp}
      iconBoxClassName={gainLossBoxClass(result.totalReturnPercent)}
      iconClassName={gainLossClass(result.totalReturnPercent)}
      testId={returnRateTestId}
    />

    <StatCard
      label="연평균 수익률"
      value={hasValue(metrics?.cagr) ? formatSigned(metrics?.cagr, formatPercent) : '—'}
      valueClassName={hasValue(metrics?.cagr) ? gainLossClass(metrics?.cagr) : 'text-tx-3'}
      sub={hasValue(metrics?.cagr)
        ? <>{timeWeighted && '입금을 뺀 시간가중 · '}{otherLabel} 기준 {formatSigned(other?.cagr, formatPercent)}</>
        : noMetrics ? '지표 없음' : '1년 미만이면 내지 않음'}
      icon={CalendarClock}
    />

    <StatCard
      label="평가 가치"
      value={<>₩{formatNumber(result.totalAssetKrw)}</>}
      sub={
        <>
          주식: ₩{formatNumber(result.currentValueKrw)}, 현금: ₩
          {formatNumber(cashKrw(result))}
        </>
      }
      icon={DollarSign}
    />
  </div>
);
