import React from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import {
  portfolioApi,
  type ProfitFlowPeriod,
  type ProfitFlowResponse,
  type ProfitHolding,
  type ProfitPoint,
  type ProfitSale,
} from '../../../services/api';
import { formatKrw } from '../../../utils/format';
import { gainLossClass } from '../../../utils/gainLoss';
import { ChartCard } from '@components/charts/backtest/shared/ChartPanel';
import { formatKrwTick, formatMonthTick } from '@components/charts/backtest/shared/chartStyle';
import { SignedStat, StatsCards, StatsLoadError, StatsLoading, StatsNotice, ValueStat } from './StatsCards';
import { StatsBarChart } from './StatsBarChart';
import { StatsFold } from './StatsFold';
import { StatsTable, type StatsColumn } from './StatsTable';
import {
  formatQuantity,
  NO_VALUE_CLASS,
  signedKrw,
  signedPercent,
  signedUsd,
  usd,
  withAccountColumn,
} from './statsFormat';

const SALE_COLUMNS: StatsColumn<ProfitSale>[] = [
  { header: '날짜', cell: (sale) => sale.date },
  { header: '종목', cell: (sale) => sale.symbol },
  { header: '수량', numeric: true, cell: (sale) => formatQuantity(sale.quantity) },
  { header: '판 가격 $', numeric: true, cell: (sale) => usd(sale.priceUsd) },
  {
    header: '실현 손익 $',
    numeric: true,
    tone: (sale) => gainLossClass(sale.realizedUsd),
    cell: (sale) => signedUsd(sale.realizedUsd),
  },
  {
    header: '실현 손익 ₩',
    numeric: true,
    tone: (sale) => (sale.fxRate == null ? NO_VALUE_CLASS : gainLossClass(sale.realizedKrw)),
    cell: (sale) => (sale.fxRate == null ? '-' : signedKrw(sale.realizedKrw)),
  },
  {
    header: '수익률',
    numeric: true,
    tone: (sale) => gainLossClass(sale.returnPercent),
    cell: (sale) => signedPercent(sale.returnPercent),
  },
];

const HOLDING_COLUMNS: StatsColumn<ProfitHolding>[] = [
  { header: '종목', cell: (holding) => holding.symbol },
  { header: '수량', numeric: true, cell: (holding) => formatQuantity(holding.quantity) },
  { header: '평균 단가 $', numeric: true, cell: (holding) => usd(holding.averagePriceUsd) },
  {
    header: '종가 $',
    numeric: true,
    tone: (holding) => (holding.closeUsd == null ? NO_VALUE_CLASS : 'text-tx-1'),
    cell: (holding) =>
      holding.closeUsd == null ? (
        '-'
      ) : (
        <>
          {usd(holding.closeUsd)}
          <span className="block text-xs text-tx-2">{holding.closeDate}</span>
        </>
      ),
  },
  {
    header: '평가 손익 $',
    numeric: true,
    tone: (holding) => gainLossClass(holding.unrealizedUsd),
    cell: (holding) => signedUsd(holding.unrealizedUsd),
  },
  {
    header: '평가 손익 ₩',
    numeric: true,
    tone: (holding) => gainLossClass(holding.unrealizedKrw),
    cell: (holding) => signedKrw(holding.unrealizedKrw),
  },
  {
    header: '수익률',
    numeric: true,
    tone: (holding) => gainLossClass(holding.returnPercent),
    cell: (holding) => signedPercent(holding.returnPercent),
  },
];

// 수익 금액과 그 날짜 투자 원금 대비 %, 날짜 · 원금(원금이 0 이면 날짜만)
const ProfitStat: React.FC<{ label: string; point: ProfitPoint | null; dateSuffix?: string }> = ({
  label,
  point,
  dateSuffix = '',
}) => {
  const invested = point && point.investedKrw > 0 ? point.investedKrw : null;
  return (
    <SignedStat
      label={label}
      amount={point?.profitKrw ?? null}
      percent={point && invested ? (point.profitKrw / invested) * 100 : null}
      details={point ? [`${point.date}${dateSuffix}`, invested ? `원금 ${formatKrw(Math.round(invested))}` : null] : []}
    />
  );
};

interface ProfitTabProps {
  accountId: number | 'all';
  period: ProfitFlowPeriod;
  // 현재 · 최고 · 최저 수익 카드의 수익 흐름
  flow?: ProfitFlowResponse;
  // 오늘 총자산(원화)과 이름 줄 오른쪽 계좌 이름 · 계좌 수
  assets: number;
  scope?: string;
  // 전체 계좌 판 거래의 계좌 이름
  accountNames: Record<number, string>;
}

// 계좌 통계 수익 탭(총자산 · 수익 카드, 달마다 수익 막대, 판 거래 · 들고 있는 종목 표)
export const ProfitTab: React.FC<ProfitTabProps> = ({ accountId, period, flow, assets, scope, accountNames }) => {
  const detail = useQuery({
    queryKey: ['portfolio', 'stats', 'profit', accountId, period],
    queryFn: async () => (await portfolioApi.getProfitDetail(accountId, period)).data,
    // 다시 불러오는 동안 앞 내역 · 열어 둔 펼침 유지
    placeholderData: keepPreviousData,
  });
  const points = flow?.points ?? [];
  const last = points.length > 0 ? points[points.length - 1] : null;

  const renderDetail = () => {
    if (detail.isPending) return <StatsLoading />;
    if (detail.isError) {
      return <StatsLoadError message="수익 내역을 불러오지 못했습니다" onRetry={() => detail.refetch()} />;
    }
    const { monthly, realizedKrw, unrealizedKrw, sales, holdings } = detail.data;
    if (monthly.length === 0 && sales.length === 0 && holdings.length === 0) {
      return <StatsNotice>이 기간에 거래가 없습니다</StatsNotice>;
    }
    return (
      <>
        {monthly.length > 0 && (
          <ChartCard title="달마다 수익" subtitle="그 달 마지막 거래일 수익 − 앞 달 마지막 거래일 수익">
            <StatsBarChart
              data={monthly.map((month) => ({ label: month.month, value: month.profitKrw }))}
              name="수익"
              formatValue={signedKrw}
              formatAxis={formatKrwTick}
              formatLabel={formatMonthTick}
            />
          </ChartCard>
        )}
        <StatsFold
          summary={
            <>
              실현 <span className={gainLossClass(realizedKrw)}>{signedKrw(realizedKrw)}</span> · 평가{' '}
              <span className={gainLossClass(unrealizedKrw)}>{signedKrw(unrealizedKrw)}</span>
            </>
          }
        >
          <StatsTable
            title="판 거래"
            columns={accountId === 'all' ? withAccountColumn(SALE_COLUMNS, accountNames) : SALE_COLUMNS}
            rows={sales}
            rowKey={(sale) => sale.tradeId}
            empty="이 기간에 판 거래가 없습니다"
          />
          <StatsTable
            title="들고 있는 종목"
            columns={HOLDING_COLUMNS}
            rows={holdings}
            rowKey={(holding) => holding.symbol}
            empty="들고 있는 종목이 없습니다"
          />
        </StatsFold>
      </>
    );
  };

  return (
    <div className="space-y-6">
      <StatsCards
        top={[
          <ValueStat label="오늘 총자산" aside={scope} value={formatKrw(Math.round(assets))} detail="현금 + 보유 평가액" />,
          <ProfitStat label="현재 수익" point={last} dateSuffix=" 기준" />,
        ]}
        bottom={[
          <ProfitStat label="최고 수익" point={flow?.best ?? null} />,
          <ProfitStat label="최저 수익" point={flow?.worst ?? null} />,
        ]}
      />
      {renderDetail()}
    </div>
  );
};
