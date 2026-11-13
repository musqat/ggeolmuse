import React, { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft } from 'lucide-react';
import { useAuth } from '../hooks/useAuth';
import { useAccountData } from '../hooks/useAccountData';
import { PageSpinner } from '../components/common/Spinner';
import { ProfitFlowChart } from '../components/account/ProfitFlowChart';
import { ProfitTab } from '../components/account/stats/ProfitTab';
import { SymbolsTab } from '../components/account/stats/SymbolsTab';
import { DividendsTab } from '../components/account/stats/DividendsTab';
import { FxTab } from '../components/account/stats/FxTab';
import { HabitsTab } from '../components/account/stats/HabitsTab';
import { CARD_CLASS, StatsLoadError, StatsLoading } from '../components/account/stats/StatsCards';
import { StatsFoldState } from '../components/account/stats/StatsFold';
import { portfolioApi, type ProfitFlowPeriod } from '../services/api';

const PERIODS: { value: ProfitFlowPeriod; label: string }[] = [
  { value: '1M', label: '1달' },
  { value: '3M', label: '3달' },
  { value: '1Y', label: '1년' },
  { value: 'ALL', label: '전체' },
];

type StatsTab = 'profit' | 'symbols' | 'dividends' | 'fx' | 'habits';

const TABS: { value: StatsTab; label: string }[] = [
  { value: 'profit', label: '수익' },
  { value: 'symbols', label: '종목' },
  { value: 'dividends', label: '배당' },
  { value: 'fx', label: '환율' },
  { value: 'habits', label: '거래 패턴' },
];

// 계좌 통계 화면(계좌 · 기간마다 수익 흐름 그래프와 주제 탭)
const AccountStats: React.FC = () => {
  const { isAuthenticated } = useAuth();
  const [accountId, setAccountId] = useState<number | 'all'>('all');
  const [period, setPeriod] = useState<ProfitFlowPeriod>('ALL');
  const [tab, setTab] = useState<StatsTab>('profit');
  const [compare, setCompare] = useState(false);
  // 탭마다 자세히 펼치기 열림
  const [openFolds, setOpenFolds] = useState<Partial<Record<StatsTab, boolean>>>({});
  const tabButtons = useRef<(HTMLButtonElement | null)[]>([]);
  const { accounts, totalAssets, assetsOf, loading } = useAccountData(isAuthenticated);

  const flow = useQuery({
    queryKey: ['portfolio', 'stats', 'flow', accountId, period],
    queryFn: async () => (await portfolioApi.getProfitFlow(accountId, period)).data,
    enabled: isAuthenticated,
  });
  const data = flow.data;
  const hasPoints = !!data && data.points.length > 0;

  const market = useQuery({
    queryKey: ['portfolio', 'stats', 'market', accountId, period],
    queryFn: async () => (await portfolioApi.getMarketComparison(accountId, period)).data,
    enabled: isAuthenticated && compare && hasPoints,
  });

  if (loading) {
    return <PageSpinner />;
  }

  const assets = accountId === 'all' ? totalAssets : assetsOf(accountId);
  const scope =
    accountId === 'all'
      ? `${accounts.length}개 계좌`
      : accounts.find((account) => account.accountId === accountId)?.accountName;
  const accountNames = Object.fromEntries(accounts.map((account) => [account.accountId, account.accountName]));

  // 왼쪽 · 오른쪽 화살표로 옆 탭(양 끝에서는 반대쪽 끝)
  const moveTab = (event: React.KeyboardEvent, index: number) => {
    const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
    if (step === 0) return;
    event.preventDefault();
    const next = (index + step + TABS.length) % TABS.length;
    setTab(TABS[next].value);
    tabButtons.current[next]?.focus();
  };

  const renderChart = () => {
    if (flow.isPending) {
      return <StatsLoading />;
    }
    if (flow.isError) {
      return <StatsLoadError message="수익 흐름을 불러오지 못했습니다" onRetry={() => flow.refetch()} />;
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
    return (
      <ProfitFlowChart
        flow={data}
        compare={{
          on: compare,
          onToggle: () => setCompare((on) => !on),
          status: market.status,
          data: market.data,
          onRetry: () => market.refetch(),
        }}
      />
    );
  };

  const renderPanel = () => {
    switch (tab) {
      case 'profit':
        return (
          <ProfitTab
            accountId={accountId}
            period={period}
            flow={data}
            assets={assets}
            scope={scope}
            accountNames={accountNames}
          />
        );
      case 'symbols':
        return <SymbolsTab accountId={accountId} period={period} />;
      case 'dividends':
        return <DividendsTab accountId={accountId} period={period} accountNames={accountNames} />;
      case 'fx':
        return <FxTab accountId={accountId} period={period} accountNames={accountNames} />;
      case 'habits':
        return <HabitsTab accountId={accountId} period={period} />;
    }
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

        <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between sm:border-b sm:border-line/50">
          <div
            role="tablist"
            aria-label="통계 주제"
            className="flex gap-1 overflow-x-auto border-b border-line/50 sm:overflow-visible sm:border-b-0"
          >
            {TABS.map((option, index) => {
              const selected = option.value === tab;
              return (
                <button
                  key={option.value}
                  ref={(element) => {
                    tabButtons.current[index] = element;
                  }}
                  type="button"
                  role="tab"
                  id={`stats-tab-${option.value}`}
                  aria-selected={selected}
                  aria-controls={selected ? `stats-panel-${option.value}` : undefined}
                  tabIndex={selected ? 0 : -1}
                  onClick={() => setTab(option.value)}
                  onKeyDown={(event) => moveTab(event, index)}
                  className={`shrink-0 whitespace-nowrap px-3 py-2 text-lg font-semibold border-b-2 sm:-mb-px ${
                    selected ? 'border-brand text-tx-1' : 'border-transparent text-tx-2 hover:text-tx-1'
                  }`}
                >
                  {option.label}
                </button>
              );
            })}
          </div>
          <div
            role="group"
            aria-label="기간"
            className="self-start sm:self-auto inline-flex rounded-btn border border-line/50 bg-surface p-0.5 sm:mb-2"
          >
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

        {renderChart()}

        <div role="tabpanel" id={`stats-panel-${tab}`} aria-labelledby={`stats-tab-${tab}`} tabIndex={0}>
          <StatsFoldState
            open={!!openFolds[tab]}
            onOpenChange={(open) => setOpenFolds((folds) => ({ ...folds, [tab]: open }))}
          >
            {renderPanel()}
          </StatsFoldState>
        </div>
      </div>
    </div>
  );
};

export default AccountStats;
