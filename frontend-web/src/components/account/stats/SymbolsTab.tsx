import React from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { portfolioApi, type ProfitFlowPeriod, type SymbolContribution } from '../../../services/api';
import { formatKrw, formatPercent } from '../../../utils/format';
import { gainLossClass } from '../../../utils/gainLoss';
import { ChartCard } from '@components/charts/backtest/shared/ChartPanel';
import { SignedStat, StatsCards, StatsLoadError, StatsLoading, StatsNotice, ValueStat } from './StatsCards';
import { StatsBarChart } from './StatsBarChart';
import { StatsFold } from './StatsFold';
import { StatsTable, type StatsColumn } from './StatsTable';
import { signedKrw } from './statsFormat';

// 막대로 그리는 종목 수(나머지는 펼침 표)
const BAR_LIMIT = 8;

const SYMBOL_COLUMNS: StatsColumn<SymbolContribution>[] = [
  {
    header: '종목',
    cell: (item) => (
      <>
        {item.symbol}
        {item.held && <span className="ml-1.5 px-1.5 py-0.5 rounded bg-elevated text-xs text-tx-2">보유</span>}
      </>
    ),
  },
  {
    header: '실현',
    numeric: true,
    tone: (item) => gainLossClass(item.realizedKrw),
    cell: (item) => signedKrw(item.realizedKrw),
  },
  {
    header: '평가',
    numeric: true,
    tone: (item) => gainLossClass(item.unrealizedKrw),
    cell: (item) => signedKrw(item.unrealizedKrw),
  },
  { header: '배당', numeric: true, cell: (item) => formatKrw(Math.round(item.dividendKrw)) },
  { header: '수수료', numeric: true, cell: (item) => formatKrw(Math.round(item.feeKrw)) },
  {
    header: '합',
    numeric: true,
    tone: (item) => gainLossClass(item.totalKrw),
    cell: (item) => signedKrw(item.totalKrw),
  },
  {
    header: '비율',
    numeric: true,
    tone: (item) => gainLossClass(item.sharePercent),
    cell: (item) => formatPercent(item.sharePercent),
  },
];

// 합이 가장 큰 종목(양수만) · 가장 작은 종목(음수만)
const extremes = (symbols: SymbolContribution[]) => {
  const most = symbols.reduce((a, b) => (b.totalKrw > a.totalKrw ? b : a));
  const least = symbols.reduce((a, b) => (b.totalKrw < a.totalKrw ? b : a));
  return { best: most.totalKrw > 0 ? most : null, worst: least.totalKrw < 0 ? least : null };
};

const SymbolStat: React.FC<{ label: string; item: SymbolContribution | null }> = ({ label, item }) => (
  <SignedStat label={label} subject={item?.symbol} amount={item?.totalKrw ?? null} percent={item?.sharePercent} />
);

// 계좌 통계 종목 탭(기간 수익 · 종목 수 · 가장 많이 번 · 잃은 종목 카드, 종목별 비율 막대, 종목별 기여 표)
export const SymbolsTab: React.FC<{ accountId: number | 'all'; period: ProfitFlowPeriod }> = ({ accountId, period }) => {
  const contributions = useQuery({
    queryKey: ['portfolio', 'stats', 'symbols', accountId, period],
    queryFn: async () => (await portfolioApi.getSymbolContributions(accountId, period)).data,
    // 다시 불러오는 동안 앞 표 · 열어 둔 펼침 유지
    placeholderData: keepPreviousData,
  });

  if (contributions.isPending) return <StatsLoading />;
  if (contributions.isError) {
    return <StatsLoadError message="종목별 기여를 불러오지 못했습니다" onRetry={() => contributions.refetch()} />;
  }
  const { totalKrw, symbols } = contributions.data;
  if (symbols.length === 0) return <StatsNotice>이 기간에 거래한 종목이 없습니다</StatsNotice>;

  const { best, worst } = extremes(symbols);
  const heldCount = symbols.filter((item) => item.held).length;

  return (
    <div className="space-y-6">
      <StatsCards
        top={[
          <SignedStat label="기간 수익" amount={totalKrw} details={['실현 + 평가 + 배당 − 수수료']} />,
          <ValueStat label="종목 수" value={`거래한 ${symbols.length}개`} detail={`들고 있는 ${heldCount}개`} />,
        ]}
        bottom={[
          <SymbolStat label="가장 많이 번 종목" item={best} />,
          <SymbolStat label="가장 많이 잃은 종목" item={worst} />,
        ]}
      />
      <ChartCard
        title="종목별 비율"
        subtitle={`종목 합 ÷ 모든 종목 합 절댓값의 합${
          symbols.length > BAR_LIMIT ? ` · 위 ${BAR_LIMIT}개, 나머지는 자세히 펼치기 표` : ''
        }`}
      >
        <StatsBarChart
          horizontal
          data={symbols.slice(0, BAR_LIMIT).map((item) => ({ label: item.symbol, value: item.sharePercent }))}
          name="비율"
          formatValue={formatPercent}
          formatAxis={(value) => `${value}%`}
        />
      </ChartCard>
      <StatsFold>
        <StatsTable title="종목별 기여" columns={SYMBOL_COLUMNS} rows={symbols} rowKey={(item) => item.symbol} />
      </StatsFold>
    </div>
  );
};
