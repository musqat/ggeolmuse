import React from 'react';
import type { TradeStats } from '../../../services/api';
import { gainLossClass } from '../../../utils/gainLoss';
import { formatNumber, formatPercent, formatSigned } from '../../../utils/format';

const decimals = (value: number | null, digits: number) =>
  value == null ? '-' : formatNumber(value, { minimumFractionDigits: digits, maximumFractionDigits: digits });

// 이름 · 큰 값 · 아래 작은 줄 한 칸
const Cell: React.FC<{ label: string; detail?: React.ReactNode; children: React.ReactNode }> = ({
  label,
  detail,
  children,
}) => (
  <div className="min-w-0">
    <dt className="text-sm font-medium text-tx-2">{label}</dt>
    <dd className="text-xl sm:text-2xl font-bold mt-1 break-words text-tx-1">{children}</dd>
    {detail && <dd className="text-xs text-tx-2 mt-1">{detail}</dd>}
  </div>
);

// 부호 · 이익 손실 색 수익률 한 줄(없으면 회색 '-')
const SignedLine: React.FC<{ value: number | null }> = ({ value }) => (
  <span className={`block ${gainLossClass(value) || 'text-tx-1'}`}>{formatSigned(value, formatPercent)}</span>
);

// 파는 전략 결과의 매매 통계 카드(승률 · 평균 익절 · 손절 · 손익비 · 연평균 거래, 끝난 사이클이 없으면 안내 한 줄)
export const TradeStatsCard: React.FC<{ stats: TradeStats }> = ({ stats }) => {
  const sold = [
    stats.trailingStopCount > 0 && `고점 대비 하락 매도 ${formatNumber(stats.trailingStopCount)}번`,
    stats.partialTakeProfitCount > 0 && `부분 익절 ${formatNumber(stats.partialTakeProfitCount)}번`,
  ].filter(Boolean);

  return (
    <section className="bg-surface rounded-xl shadow-sm border border-line/50 p-4 sm:p-5 space-y-4">
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <h3 className="text-lg font-semibold text-tx-1">매매 통계</h3>
        <span className="text-sm text-tx-2">
          사이클 {formatNumber(stats.cycleCount)}번{stats.openCycle && ' · 진행 중'}
        </span>
      </div>
      {stats.cycleCount === 0 ? (
        <p className="text-sm text-tx-2">기간 안에 전부 판 사이클이 없습니다</p>
      ) : (
        <dl className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <Cell label="승률" detail={`${formatNumber(stats.winCount)}승 ${formatNumber(stats.lossCount)}패`}>
            {stats.winRatePercent == null ? '-' : formatPercent(stats.winRatePercent)}
          </Cell>
          <Cell label="평균 익절 · 손절">
            <SignedLine value={stats.averageWinPercent} />
            <SignedLine value={stats.averageLossPercent == null ? null : -stats.averageLossPercent} />
          </Cell>
          <Cell label="손익비">{decimals(stats.profitLossRatio, 2)}</Cell>
          <Cell
            label="연평균 거래"
            detail={
              stats.averageHoldingDays != null &&
              `평균 보유 ${formatNumber(stats.averageHoldingDays, { maximumFractionDigits: 1 })}일`
            }
          >
            {stats.cyclesPerYear == null ? '-' : `${decimals(stats.cyclesPerYear, 1)}번`}
          </Cell>
        </dl>
      )}
      {sold.length > 0 && <p className="text-xs text-tx-2">{sold.join(' · ')}</p>}
    </section>
  );
};
