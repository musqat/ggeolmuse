import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft } from 'lucide-react';
import { useAuth } from '../hooks/useAuth';
import { useAccountData } from '../hooks/useAccountData';
import { LoginRequired } from '../components/common/LoginRequired';
import { PageSpinner, Spinner } from '../components/common/Spinner';
import { ProfitFlowChart } from '../components/account/ProfitFlowChart';
import { portfolioApi, type ProfitFlowPeriod, type ProfitPoint } from '../services/api';
import { formatKrw, formatPercent, formatSigned } from '../utils/format';
import { gainLossClass } from '../utils/gainLoss';

const PERIODS: { value: ProfitFlowPeriod; label: string }[] = [
  { value: '1M', label: '1달' },
  { value: '3M', label: '3달' },
  { value: '1Y', label: '1년' },
  { value: 'ALL', label: '전체' },
];

const CARD_CLASS = 'bg-surface rounded-xl shadow-sm border border-line/50 p-6';
const STAT_CARD_CLASS = 'bg-surface rounded-xl shadow-sm border border-line/50 p-4 sm:p-5';

const signedKrw = (value: number) => formatSigned(Math.round(value), formatKrw);

// 수익 금액과 그 날짜 투자 원금 대비 %, 날짜 · 원금(원금이 0 이면 날짜만)
const ProfitStat: React.FC<{ label: string; point: ProfitPoint | null; dateSuffix?: string }> = ({
  label,
  point,
  dateSuffix = '',
}) => {
  const invested = point && point.investedKrw > 0 ? point.investedKrw : null;
  return (
    <div data-stat={label}>
      <p className="text-sm font-medium text-tx-2">{label}</p>
      <p className={`text-xl sm:text-2xl font-bold mt-1 ${point ? gainLossClass(point.profitKrw) : 'text-tx-1'}`}>
        {point ? signedKrw(point.profitKrw) : '-'}
        {point && invested && (
          <span className="block sm:inline text-sm sm:text-base font-semibold sm:ml-1">
            ({formatPercent((point.profitKrw / invested) * 100)})
          </span>
        )}
      </p>
      {point && (
        <p className="text-xs text-tx-2 mt-1">
          {point.date}
          {dateSuffix}
          {invested && (
            <>
              <span className="hidden sm:inline"> · </span>
              <span className="block sm:inline">원금 {formatKrw(Math.round(invested))}</span>
            </>
          )}
        </p>
      )}
    </div>
  );
};

// 계좌 통계 화면(계좌 · 기간마다 수익 흐름, 위에 오늘 총자산 · 현재 수익, 아래에 최고 · 최저 수익)
const AccountStats: React.FC = () => {
  const { isAuthenticated } = useAuth();
  const [accountId, setAccountId] = useState<number | 'all'>('all');
  const [period, setPeriod] = useState<ProfitFlowPeriod>('ALL');
  const { accounts, totalAssets, assetsOf, loading } = useAccountData(isAuthenticated);

  const flow = useQuery({
    queryKey: ['portfolio', 'stats', 'flow', accountId, period],
    queryFn: async () => (await portfolioApi.getProfitFlow(accountId, period)).data,
    enabled: isAuthenticated,
  });

  if (!isAuthenticated) {
    return <LoginRequired description="계좌 통계를 보시려면 먼저 로그인해주세요" />;
  }

  if (loading) {
    return <PageSpinner />;
  }

  const data = flow.data;
  const hasPoints = !!data && data.points.length > 0;
  const last = hasPoints ? data.points[data.points.length - 1] : null;
  const assets = accountId === 'all' ? totalAssets : assetsOf(accountId);
  const scope =
    accountId === 'all'
      ? `${accounts.length}개 계좌`
      : accounts.find((account) => account.accountId === accountId)?.accountName;

  const renderChart = () => {
    if (flow.isPending) {
      return (
        <div className={`${CARD_CLASS} flex justify-center py-16`}>
          <Spinner />
        </div>
      );
    }
    if (flow.isError) {
      return (
        <div className={`${CARD_CLASS} text-center py-12`}>
          <p className="text-tx-1 font-medium mb-4">수익 흐름을 불러오지 못했습니다</p>
          <button
            type="button"
            onClick={() => flow.refetch()}
            className="px-4 py-2 bg-elevated text-tx-1 rounded-lg hover:bg-hover transition-colors"
          >
            다시 시도
          </button>
        </div>
      );
    }
    if (!hasPoints) {
      return (
        <div className={`${CARD_CLASS} text-center py-12`}>
          <p className="text-lg font-semibold text-tx-1 mb-2">아직 수익 흐름이 없습니다</p>
          <p className="text-tx-2 mb-6">체결한 거래가 생기면 그 거래일부터 그립니다</p>
          <Link
            to="/trading"
            className="inline-block px-6 py-3 bg-brand text-brand-ink rounded-lg hover:bg-brand-dark transition-colors"
          >
            거래하러 가기
          </Link>
        </div>
      );
    }
    return <ProfitFlowChart flow={data} />;
  };

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      <div className="space-y-6">
        <div className="flex flex-col md:flex-row md:items-end md:justify-between gap-4">
          <div>
            <Link to="/account" className="inline-flex items-center gap-1 text-sm text-tx-2 hover:text-tx-1">
              <ArrowLeft className="w-4 h-4" />
              계좌 관리
            </Link>
            <h1 className="text-3xl font-bold text-tx-1 mt-1">계좌 통계</h1>
          </div>
          <label className="flex items-center gap-2 text-sm text-tx-2">
            계좌
            <select
              value={String(accountId)}
              onChange={(event) => setAccountId(event.target.value === 'all' ? 'all' : Number(event.target.value))}
              className="bg-surface border border-line rounded-btn px-3 py-2 text-tx-1"
            >
              <option value="all">전체 계좌</option>
              {accounts.map((account) => (
                <option key={account.accountId} value={account.accountId}>
                  {account.accountName}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div className="flex flex-wrap items-end justify-between gap-3 border-b border-line/50">
          <div role="tablist" aria-label="통계 주제" className="flex gap-1">
            <button
              type="button"
              role="tab"
              id="stats-tab-profit"
              aria-selected="true"
              aria-controls="stats-panel-profit"
              className="-mb-px px-3 py-2 text-lg font-semibold border-b-2 border-brand text-tx-1"
            >
              수익
            </button>
          </div>
          <div role="group" aria-label="기간" className="inline-flex rounded-btn border border-line/50 bg-surface p-0.5 mb-2">
            {PERIODS.map((option) => (
              <button
                key={option.value}
                type="button"
                aria-pressed={period === option.value}
                onClick={() => setPeriod(option.value)}
                className={`px-3 py-1 text-sm rounded-btn ${
                  period === option.value ? 'bg-elevated text-tx-1 font-medium' : 'text-tx-2 hover:text-tx-1'
                }`}
              >
                {option.label}
              </button>
            ))}
          </div>
        </div>

        <div role="tabpanel" id="stats-panel-profit" aria-labelledby="stats-tab-profit" className="space-y-6">
          {renderChart()}

          <div className="space-y-4">
            <div className={`${STAT_CARD_CLASS} grid grid-cols-1 sm:grid-cols-2 gap-4`}>
              <div data-stat="오늘 총자산">
                <div className="flex items-center justify-between gap-2 text-sm text-tx-2">
                  <span className="font-medium">오늘 총자산</span>
                  <span className="truncate">{scope}</span>
                </div>
                <p className="text-xl sm:text-2xl font-bold mt-1 text-tx-1">{formatKrw(Math.round(assets))}</p>
                <p className="text-xs text-tx-2 mt-1">현금 + 보유 평가액</p>
              </div>
              <div className="border-t border-line/50 pt-4 sm:border-t-0 sm:pt-0 sm:border-l sm:pl-6">
                <ProfitStat label="현재 수익" point={last} dateSuffix=" 기준" />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className={STAT_CARD_CLASS}>
                <ProfitStat label="최고 수익" point={data?.best ?? null} />
              </div>
              <div className={STAT_CARD_CLASS}>
                <ProfitStat label="최저 수익" point={data?.worst ?? null} />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AccountStats;
