import React from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { portfolioApi, type FxDirection, type FxExchange, type ProfitFlowPeriod } from '../../../services/api';
import { formatFxRate } from '../../../utils/format';
import { gainLossClass } from '../../../utils/gainLoss';
import { SignedStat, StatsCards, StatsLoadError, StatsLoading, StatsNotice, ValueStat } from './StatsCards';
import { StatsFold } from './StatsFold';
import { StatsTable, type StatsColumn } from './StatsTable';
import { krw, NO_VALUE_CLASS, signedKrw, usd, withAccountColumn } from './statsFormat';

const DIRECTION_LABEL: Record<FxDirection, string> = {
  KRW_TO_USD: '원화→달러',
  USD_TO_KRW: '달러→원화',
};

// 환율 효과 아래 줄
const PERIOD_LABEL: Record<ProfitFlowPeriod, string> = {
  '1M': '최근 1달',
  '3M': '최근 3달',
  '1Y': '최근 1년',
  ALL: '전체 기간',
};

const rate = (value: number | null) => (value == null ? '-' : formatFxRate(value));

// 같은 날짜 · 같은 금액 환전도 줄마다 다른 키
type ExchangeRow = FxExchange & { order: number };

// 지금 환율이 없으면 효과 칸 '-'
const exchangeColumns = (hasRate: boolean): StatsColumn<ExchangeRow>[] => [
  { header: '날짜', cell: (row) => row.date },
  { header: '방향', cell: (row) => DIRECTION_LABEL[row.direction] },
  { header: '원화', numeric: true, cell: (row) => krw(row.krwAmount) },
  { header: '달러', numeric: true, cell: (row) => usd(row.usdAmount) },
  { header: '환율', numeric: true, cell: (row) => formatFxRate(row.rate) },
  {
    header: '지금 기준 효과 ₩',
    numeric: true,
    tone: (row) => (hasRate ? gainLossClass(row.effectKrw) : NO_VALUE_CLASS),
    cell: (row) => (hasRate ? signedKrw(row.effectKrw) : '-'),
  },
];

// 계좌 통계 환율 탭(환율 효과 · 지금 환율 · 환전한 달러 원금 · 기간 안 환전 카드, 환전 기록 표)
interface FxTabProps {
  accountId: number | 'all';
  period: ProfitFlowPeriod;
  // 전체 계좌 환전 기록의 계좌 이름
  accountNames: Record<number, string>;
}

export const FxTab: React.FC<FxTabProps> = ({ accountId, period, accountNames }) => {
  const fx = useQuery({
    queryKey: ['portfolio', 'stats', 'fx', accountId, period],
    queryFn: async () => (await portfolioApi.getFxStats(accountId, period)).data,
    // 다시 불러오는 동안 앞 기록 · 열어 둔 펼침 유지
    placeholderData: keepPreviousData,
  });

  if (fx.isPending) return <StatsLoading />;
  if (fx.isError) {
    return <StatsLoadError message="환율 효과를 불러오지 못했습니다" onRetry={() => fx.refetch()} />;
  }
  const { fxEffectKrw, currentRate, averageRate, heldUsd, krwToUsdKrw, usdToKrwUsd, exchangeCount, exchanges } =
    fx.data;
  if (exchanges.length === 0 && heldUsd === 0 && fxEffectKrw === 0) {
    return <StatsNotice>이 기간에 환전 기록이 없습니다</StatsNotice>;
  }
  const hasRate = currentRate != null;
  const columns = exchangeColumns(hasRate);

  return (
    <div className="space-y-6">
      <div className="space-y-3">
        <StatsCards
          top={[
            <SignedStat label="환율 효과" amount={hasRate ? fxEffectKrw : null} details={[PERIOD_LABEL[period]]} />,
            <ValueStat label="지금 환율" value={rate(currentRate)} detail={`평균 환전 환율 ${rate(averageRate)}`} />,
          ]}
          bottom={[
            <ValueStat label="환전한 달러 원금" value={usd(heldUsd)} detail="받은 달러 − 판 달러" />,
            <ValueStat
              label="기간 안 환전"
              value={`${exchangeCount}회`}
              detail={
                <>
                  <span className="block">원화→달러 {krw(krwToUsdKrw)}</span>
                  <span className="block">달러→원화 {usd(usdToKrwUsd)}</span>
                </>
              }
            />,
          ]}
        />
        <p className="text-xs text-tx-2">환전한 때와 지금의 환율 차이 · 수익 흐름에는 들어가지 않음</p>
      </div>
      <StatsFold>
        <StatsTable
          title="환전 기록"
          columns={accountId === 'all' ? withAccountColumn(columns, accountNames) : columns}
          rows={exchanges.map((exchange, order): ExchangeRow => ({ ...exchange, order }))}
          rowKey={(row) => row.order}
          empty="이 기간에 환전 기록이 없습니다"
        />
      </StatsFold>
    </div>
  );
};
