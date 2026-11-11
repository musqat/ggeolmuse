import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { portfolioApi, type ProfitFlowPeriod, type SymbolTrades, type Weekday, type WeekdayTrades } from '../../../services/api';
import { formatNumber, formatPercent } from '../../../utils/format';
import { ChartCard } from '@components/charts/backtest/shared/ChartPanel';
import { StatsCards, StatsLoadError, StatsLoading, StatsNotice, ValueStat } from './StatsCards';
import { StatsBarChart } from './StatsBarChart';
import { StatsFold } from './StatsFold';
import { StatsTable, type StatsColumn } from './StatsTable';
import { usd } from './statsFormat';

const WEEKDAY_LABEL: Record<Weekday, string> = {
  MONDAY: '월',
  TUESDAY: '화',
  WEDNESDAY: '수',
  THURSDAY: '목',
  FRIDAY: '금',
  SATURDAY: '토',
  SUNDAY: '일',
};

// 월 ~ 금은 늘, 토 · 일은 체결이 있을 때만
const weekdayBars = (weekdays: WeekdayTrades[]) =>
  weekdays
    .filter((day) => (day.weekday !== 'SATURDAY' && day.weekday !== 'SUNDAY') || day.count > 0)
    .map((day) => ({ label: WEEKDAY_LABEL[day.weekday], value: day.count }));

const SYMBOL_COLUMNS: StatsColumn<SymbolTrades>[] = [
  { header: '종목', cell: (item) => item.symbol },
  { header: '체결 수', numeric: true, cell: (item) => formatNumber(item.tradeCount) },
  { header: '매수', numeric: true, cell: (item) => formatNumber(item.buyCount) },
  { header: '매도', numeric: true, cell: (item) => formatNumber(item.sellCount) },
  { header: '체결 금액 $', numeric: true, cell: (item) => usd(item.amountUsd) },
];

// 계좌 통계 거래 패턴 탭(매수 · 매도 횟수 · 거래한 날 · 평균 보유 기간 · 승률 카드, 요일별 체결 막대, 많이 거래한 종목 표)
export const HabitsTab: React.FC<{ accountId: number | 'all'; period: ProfitFlowPeriod }> = ({ accountId, period }) => {
  const habits = useQuery({
    queryKey: ['portfolio', 'stats', 'habits', accountId, period],
    queryFn: async () => (await portfolioApi.getTradingHabits(accountId, period)).data,
  });

  if (habits.isPending) return <StatsLoading />;
  if (habits.isError) {
    return <StatsLoadError message="거래 패턴을 불러오지 못했습니다" onRetry={() => habits.refetch()} />;
  }
  const { buyCount, sellCount, tradingDays, averageHoldingDays, winCount, lossCount, winRatePercent, weekdays, topSymbols } =
    habits.data;
  if (buyCount + sellCount === 0) return <StatsNotice>이 기간에 거래가 없습니다</StatsNotice>;

  return (
    <div className="space-y-6">
      <StatsCards
        top={[
          <ValueStat label="거래 횟수" value={`매수 ${formatNumber(buyCount)}회 · 매도 ${formatNumber(sellCount)}회`} />,
          <ValueStat label="거래한 날" value={`${formatNumber(tradingDays)}일`} detail="체결이 있는 거래일" />,
        ]}
        bottom={[
          <ValueStat
            label="평균 보유 기간"
            value={averageHoldingDays == null ? '-' : `${formatNumber(averageHoldingDays, { maximumFractionDigits: 1 })}일`}
            detail={averageHoldingDays != null && '먼저 산 주식부터 판 것으로 계산'}
          />,
          <ValueStat
            label="승률"
            value={winRatePercent == null ? '-' : formatPercent(winRatePercent)}
            detail={winRatePercent != null && `${formatNumber(winCount)}승 ${formatNumber(lossCount)}패`}
          />,
        ]}
      />
      <ChartCard title="요일별 체결" subtitle="거래일의 요일마다 체결 수">
        <StatsBarChart
          counts
          data={weekdayBars(weekdays)}
          name="체결 수"
          formatValue={(value) => `${formatNumber(value)}회`}
          formatAxis={(value) => `${value}`}
        />
      </ChartCard>
      <StatsFold summary={`종목 ${formatNumber(topSymbols.length)}개`}>
        <StatsTable title="많이 거래한 종목" columns={SYMBOL_COLUMNS} rows={topSymbols} rowKey={(item) => item.symbol} />
      </StatsFold>
    </div>
  );
};
