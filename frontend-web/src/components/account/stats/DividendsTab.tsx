import React from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { portfolioApi, type DividendPayout, type ProfitFlowPeriod, type SymbolDividend } from '../../../services/api';
import { formatFxRate, formatNumber, formatPercent } from '../../../utils/format';
import { ChartCard } from '@components/charts/backtest/shared/ChartPanel';
import { formatKrwTick, formatMonthTick } from '@components/charts/backtest/shared/chartStyle';
import { SignedStat, StatsCards, StatsLoadError, StatsLoading, StatsNotice, ValueStat } from './StatsCards';
import { StatsBarChart } from './StatsBarChart';
import { StatsFold } from './StatsFold';
import { StatsTable, type StatsColumn } from './StatsTable';
import { formatQuantity, krw, NO_VALUE_CLASS, usd, usdPerShare, withAccountColumn } from './statsFormat';

// 배당락일 환율이 하나도 없어 원화를 모르는 합(달러는 있는데 원화 0)
const noKrw = (item: { netKrw: number; netUsd: number }) => item.netKrw === 0 && item.netUsd > 0;

const symbolTone = (item: SymbolDividend) => (noKrw(item) ? NO_VALUE_CLASS : 'text-tx-1');

const SYMBOL_COLUMNS: StatsColumn<SymbolDividend>[] = [
  { header: '종목', cell: (item) => item.symbol },
  { header: '횟수', numeric: true, cell: (item) => item.payoutCount },
  { header: '세후 $', numeric: true, cell: (item) => usd(item.netUsd) },
  { header: '세후 ₩', numeric: true, tone: symbolTone, cell: (item) => (noKrw(item) ? '-' : krw(item.netKrw)) },
  {
    header: '비율',
    numeric: true,
    tone: symbolTone,
    cell: (item) => (noKrw(item) ? '-' : formatPercent(item.sharePercent)),
  },
];

// 환율 · 세후 원화 칸 글자색(배당락일 환율이 없으면 '-' 색)
const rateTone = (payout: DividendPayout) => (payout.fxRate == null ? NO_VALUE_CLASS : 'text-tx-1');

const PAYOUT_COLUMNS: StatsColumn<DividendPayout>[] = [
  { header: '배당락일', cell: (payout) => payout.exDate },
  { header: '종목', cell: (payout) => payout.symbol },
  { header: '수량', numeric: true, cell: (payout) => formatQuantity(payout.shares) },
  { header: '주당 $', numeric: true, cell: (payout) => usdPerShare(payout.dividendPerShareUsd) },
  { header: '세전 $', numeric: true, cell: (payout) => usd(payout.grossUsd) },
  { header: '세금 $', numeric: true, cell: (payout) => usd(payout.taxUsd) },
  { header: '세후 $', numeric: true, cell: (payout) => usd(payout.netUsd) },
  {
    header: '환율',
    numeric: true,
    tone: rateTone,
    cell: (payout) => (payout.fxRate == null ? '-' : formatFxRate(payout.fxRate)),
  },
  {
    header: '세후 ₩',
    numeric: true,
    tone: rateTone,
    cell: (payout) => (payout.fxRate == null ? '-' : krw(payout.netKrw)),
  },
];

// 계좌 통계 배당 탭(받은 배당 · 지급 횟수 · 가장 많이 받은 종목 · 원천징수 세금 카드, 달마다 배당 막대, 종목별 · 지급 내역 표)
interface DividendsTabProps {
  accountId: number | 'all';
  period: ProfitFlowPeriod;
  // 전체 계좌 지급 내역의 계좌 이름
  accountNames: Record<number, string>;
}

export const DividendsTab: React.FC<DividendsTabProps> = ({ accountId, period, accountNames }) => {
  const dividends = useQuery({
    queryKey: ['portfolio', 'stats', 'dividends', accountId, period],
    queryFn: async () => (await portfolioApi.getDividendStats(accountId, period)).data,
    // 다시 불러오는 동안 앞 내역 · 열어 둔 펼침 유지
    placeholderData: keepPreviousData,
  });

  if (dividends.isPending) return <StatsLoading />;
  if (dividends.isError) {
    return <StatsLoadError message="배당을 불러오지 못했습니다" onRetry={() => dividends.refetch()} />;
  }
  const { netKrw, netUsd, grossUsd, taxUsd, payoutCount, monthly, symbols, payouts } = dividends.data;
  if (payouts.length === 0) return <StatsNotice>이 기간에 받은 배당이 없습니다</StatsNotice>;

  // 세후 원화가 가장 큰 종목(원화가 0 이면 없음)
  const most = symbols.length > 0 && symbols[0].netKrw > 0 ? symbols[0] : null;
  const missing = payouts.filter((payout) => payout.fxRate == null).length;
  const bars = monthly
    .filter((month) => !noKrw(month))
    .map((month) => ({ label: month.month, value: month.netKrw }));

  return (
    <div className="space-y-6">
      <div className="space-y-3">
        <StatsCards
          top={[
            <ValueStat
              label="받은 배당"
              aside="세후"
              value={noKrw({ netKrw, netUsd }) ? '-' : krw(netKrw)}
              detail={usd(netUsd)}
            />,
            <ValueStat label="지급 횟수" value={`${payoutCount}회`} detail={`종목 ${symbols.length}개`} />,
          ]}
          bottom={[
            <SignedStat
              label="가장 많이 받은 종목"
              subject={most?.symbol}
              amount={most?.netKrw ?? null}
              percent={most?.sharePercent}
              unsigned
            />,
            <ValueStat label="원천징수 세금" value={usd(taxUsd)} detail={`세전 ${usd(grossUsd)}`} />,
          ]}
        />
        {missing > 0 && (
          <p className="text-xs text-tx-2">{`배당락일 환율이 없는 배당 ${formatNumber(missing)}건은 원화 합에 들지 않음`}</p>
        )}
      </div>
      {bars.length > 0 && (
        <ChartCard title="달마다 배당" subtitle="배당락일 달마다 세후 배당(배당락일 환율)">
          <StatsBarChart
            data={bars}
            name="세후 배당"
            formatValue={krw}
            formatAxis={formatKrwTick}
            formatLabel={formatMonthTick}
          />
        </ChartCard>
      )}
      <StatsFold>
        <StatsTable title="종목별 배당" columns={SYMBOL_COLUMNS} rows={symbols} rowKey={(item) => item.symbol} />
        <StatsTable
          title="지급 내역"
          columns={accountId === 'all' ? withAccountColumn(PAYOUT_COLUMNS, accountNames) : PAYOUT_COLUMNS}
          rows={payouts}
          rowKey={(payout) => `${payout.exDate}-${payout.accountId}-${payout.symbol}`}
        />
      </StatsFold>
    </div>
  );
};
