import React, { useMemo, useState } from 'react';
import { AccumulationChart } from '@components/charts/backtest/AccumulationChart';
import type { StrategyResponse } from '../../../services/api';
import { gainLossClass } from '../../../utils/gainLoss';
import { formatBalanceAmount, formatKrw, formatPercent, formatSigned, formatNumber, formatUsd } from '../../../utils/format';
import { ResultOverview } from './ResultOverview';
import { FxImpactSection } from './FxImpactSection';
import { DetailRow, DetailSection } from './DetailSection';
import { PurchaseHistory } from './PurchaseHistory';
import { DefaultFxNotice } from './DefaultFxNotice';
import type { MetricCurrency } from './CurrencyToggle';
import { ResultFold } from './ResultFold';
import { RiskDetail } from './RiskDetail';
import { detailMeta, pickMetrics, purchaseCount, sellCount } from './resultValues';
import { strategyDeposits } from './assetSeries';

interface DcaConditionalResultProps {
  result: StrategyResponse;
  symbol: string;
  // 결과에 시작일이 없을 때 차트 시작일
  startDate: string;
}

// 매수 0회 · 예산 부족 구간 안내 한 줄(없으면 null)
const purchaseNotice = (bought: number, span: StrategyResponse['skippedSpan']) => {
  const shortfall = span ? `예산이 모자라 ${span.firstDate}부터 ${span.rounds}회를 사지 않았습니다.` : null;
  if (bought > 0) return shortfall;
  return `매수 0회: ${shortfall ?? '기간 안에 조건이 맞아 산 날이 없습니다.'}`;
};

// 적립식 · 조건부 결과: 카드 · 자산 추이 · 주가 추이 탭, 그 아래 상세 · 매매 내역을 접어 둠
export const DcaConditionalResult: React.FC<DcaConditionalResultProps> = ({
  result,
  symbol,
  startDate,
}) => {
  const [currency, setCurrency] = useState<MetricCurrency>('KRW');
  const { metrics, other, otherLabel } = pickMetrics(result, currency);
  const transactions = useMemo(() => result.transactions ?? [], [result.transactions]);
  const skippedRounds = result.skippedRounds ?? [];
  const deposits = useMemo(() => strategyDeposits(transactions), [transactions]);
  // 투자일이 휴장일이라 다른 날 산 번수
  const moved = transactions.filter((tx) => tx.actualDate && tx.actualDate !== tx.date).length;
  const sold = sellCount(transactions);
  const notice = purchaseNotice(purchaseCount(transactions), result.skippedSpan);

  return (
    <div className="space-y-6">
      {notice && (
        <p className="text-sm text-warning bg-warning-soft/10 border border-warning-soft/40 rounded-lg px-3 py-2">{notice}</p>
      )}
      {moved > 0 && (
        <p className="text-sm text-warning bg-warning-soft/10 border border-warning-soft/40 rounded-lg px-3 py-2">
          투자일이 휴장일이면 다음 거래일 종가로 삽니다(이번 결과 {moved}번).
        </p>
      )}
      <DefaultFxNotice dates={result.defaultFxDates} />
      <ResultOverview
        result={result}
        investedLabel="총 투자금"
        invested={result.totalInvested}
        deposits={deposits}
        currency={currency}
        onCurrencyChange={setCurrency}
        timeWeighted
        chartTabs={[
          {
            key: 'price',
            label: '주가 추이',
            render: () => (
              <AccumulationChart
                symbol={result.symbol || symbol}
                transactions={transactions}
                startDate={result.startDate || startDate}
                endDate={result.endDate ?? undefined}
              />
            ),
          },
        ]}
      />

      <ResultFold
        title="상세"
        meta={detailMeta(metrics, formatSigned(result.fxReturnPercent, formatPercent))}
      >
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-6">
          <RiskDetail metrics={metrics} other={other} otherLabel={otherLabel} timeWeighted />

          <DetailSection title="투자 정보">
            <DetailRow label="종목" value={result.symbol} />
            <DetailRow
              label="기간"
              value={
                <>
                  {result.startDate?.toString().substring(0, 7)} ~ {result.endDate?.toString().substring(0, 7)}
                </>
              }
            />
            <DetailRow label="매수 횟수" value={<>{purchaseCount(transactions)}회</>} />
            {sold > 0 && <DetailRow label="매도 횟수" value={<>{sold}회</>} />}
            <DetailRow label="총 투자금" value={<>₩{formatNumber(result.totalInvested)}</>} />
            <div className="flex justify-between gap-3 py-2">
              <span className="text-tx-2">주식 평가액</span>
              <div className="text-right">
                <div className="font-medium text-tx-1">₩{formatNumber(result.currentValueKrw)}</div>
                <div className="text-xs text-tx-2">
                  {result.currentValue != null && formatBalanceAmount(result.currentValue, 'USD')}
                </div>
              </div>
            </div>
          </DetailSection>

          <DetailSection title="주식 수익">
            <DetailRow label="보유 주식수" value={<>{result.totalShares?.toFixed(2)} 주</>} />
            <DetailRow
              label="평균 매수가"
              value={result.averagePrice != null ? formatUsd(result.averagePrice) : '-'}
            />
            <DetailRow label="평가일 가격" value={<>${result.currentPrice?.toFixed(2)}</>} />
            <DetailRow label="배당금 (USD)" value={<>${result.totalDividends?.toFixed(2) || '0.00'}</>} />
            {sold > 0 && (
              <>
                <DetailRow
                  label="실현 손익"
                  value={formatSigned(result.realizedProfit, formatUsd)}
                  valueClassName={`font-medium ${gainLossClass(result.realizedProfit)}`}
                />
                <DetailRow label="남은 현금" value={formatUsd(result.cashBalance ?? 0)} />
              </>
            )}
            <DetailRow
              label="총 수익 (KRW)"
              value={formatSigned(result.totalReturnKrw, formatKrw)}
              valueClassName={`font-bold ${gainLossClass(result.totalReturnKrw)}`}
            />
            <DetailRow
              label="수익률"
              value={formatSigned(result.totalReturnPercent, formatPercent)}
              valueClassName={`font-bold ${gainLossClass(result.totalReturnPercent)}`}
              last
            />
          </DetailSection>

          <FxImpactSection result={result} startLabel="평균 환율" startRate={result.averageFxRate} />
        </div>
      </ResultFold>

      {(transactions.length > 0 || skippedRounds.length > 0) && (
        <ResultFold
          title={sold > 0 ? '매매 내역' : '매수 내역'}
          meta={`매수 ${purchaseCount(transactions)}회${sold > 0 ? ` · 매도 ${sold}회` : ''} · 미매수 ${skippedRounds.length}회`}
        >
          <PurchaseHistory transactions={transactions} skippedRounds={skippedRounds} />
        </ResultFold>
      )}
    </div>
  );
};
