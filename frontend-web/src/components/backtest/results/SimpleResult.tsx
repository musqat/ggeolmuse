import React, { useMemo, useState } from 'react';
import { SimpleChart } from '@components/charts/backtest/SimpleChart';
import type { SimulationResponse } from '../../../services/api';
import { gainLossClass } from '../../../utils/gainLoss';
import { formatKrw, formatPercent, formatSigned, formatUsd, formatNumber } from '../../../utils/format';
import { ResultOverview } from './ResultOverview';
import { FxImpactSection } from './FxImpactSection';
import { DetailRow, DetailSection } from './DetailSection';
import { getTodayString } from '../../../utils/dateUtils';
import { cashKrw } from '../shared/backtestDisplay';
import { HolidayNotice } from './HolidayNotice';
import { DefaultFxNotice } from './DefaultFxNotice';
import type { MetricCurrency } from './CurrencyToggle';
import { ResultFold } from './ResultFold';
import { RiskDetail } from './RiskDetail';
import { detailMeta, pickMetrics } from './resultValues';
import { assetSeries, simpleDeposits } from './assetSeries';
import { StatCard } from './StatCard';
import { purchaseDaySeries, rankOf } from './purchaseDays';
import { PurchaseDayChart } from './PurchaseDayChart';
import { CapitalGainsTaxFold } from './CapitalGainsTaxFold';

interface SimpleResultProps {
  result: SimulationResponse;
  symbol: string;
  purchaseDate: string;
}

const dash = (value: React.ReactNode) => <span className="text-tx-3">{value}</span>;

// 단순 매수 결과: 카드(매수 타이밍 포함) · 자산 추이 · 주가 추이 · 매수일별 수익률 탭, 그 아래 예상 양도세 · 상세를 접어 둠
export const SimpleResult: React.FC<SimpleResultProps> = ({ result, symbol, purchaseDate }) => {
  const [currency, setCurrency] = useState<MetricCurrency>('KRW');
  const { metrics, other, otherLabel } = pickMetrics(result, currency);
  const deposits = useMemo(
    () => simpleDeposits(result.purchaseDate, result.investmentAmount, result.purchaseFxRate),
    [result.purchaseDate, result.investmentAmount, result.purchaseFxRate],
  );
  const purchaseDays = useMemo(
    () => purchaseDaySeries(result.purchaseDayReturns, currency),
    [result.purchaseDayReturns, currency],
  );
  const rank = rankOf(purchaseDays, result.purchaseDate);

  // 주가 툴팁에 덧붙일 보유 수익률과 여기서 샀다면 평가일까지 수익률
  const tooltipExtra = useMemo(() => {
    const own = new Map(assetSeries(result.dailyValues ?? [], deposits, currency).map((p) => [p.date, p.returnPct]));
    const ifBought = new Map(purchaseDays.map((p) => [p.date, p.returnPct]));
    const line = (label: string, value: number) => (
      <p className="text-sm text-tx-1">
        {label}: <span className={gainLossClass(value)}>{formatSigned(value, formatPercent)}</span>
      </p>
    );
    return (date: string) => {
      const ownReturn = own.get(date);
      const ifBoughtReturn = ifBought.get(date);
      if (ownReturn == null && ifBoughtReturn == null) return null;
      return (
        <>
          {ownReturn != null && line('보유 수익률', ownReturn)}
          {ifBoughtReturn != null && line('여기서 샀다면 평가일까지', ifBoughtReturn)}
        </>
      );
    };
  }, [result.dailyValues, deposits, currency, purchaseDays]);

  return (
    <div className="space-y-6">
      <HolidayNotice label="매수일" chosen={purchaseDate} traded={result.purchaseDate} />
      <DefaultFxNotice dates={result.defaultFxDates} />
      <ResultOverview
        result={result}
        investedLabel="초기 투자금"
        invested={result.investmentAmount}
        returnBasis="투자금 기준"
        deposits={deposits}
        currency={currency}
        onCurrencyChange={setCurrency}
        returnRateTestId="backtest-return-rate"
        extraCard={
          rank && rank.total >= 2 && (
            <StatCard
              // 두 칸 화면에서는 한 줄 전체
              className="sm:col-span-2 lg:col-span-1"
              label="매수 타이밍"
              value={`상위 ${formatPercent((rank.rank / rank.total) * 100)}`}
              sub={<p>매수일부터 평가일 전까지 거래일 {rank.total}일 중 {rank.rank}번째</p>}
            />
          )
        }
        chartTabs={[
          {
            key: 'price',
            label: '주가 추이',
            render: () => (
              <SimpleChart
                symbol={result.symbol || symbol}
                purchaseDate={result.purchaseDate || purchaseDate}
                optimalBuyDate={result.optimalBuyDate ?? undefined}
                optimalSellDate={result.optimalSellDate ?? undefined}
                dividendReinvestDates={result.dividendReinvestDates ?? undefined}
                endDate={result.currentDate ?? undefined}
                tooltipExtra={tooltipExtra}
              />
            ),
          },
          {
            key: 'purchase-days',
            label: '매수일별 수익률',
            render: () => (
              <PurchaseDayChart
                returns={result.purchaseDayReturns}
                currency={currency}
                purchaseDate={result.purchaseDate}
                valuationDate={result.currentDate}
              />
            ),
          },
        ]}
      />

      {result.capitalGainsTax && (
        <CapitalGainsTaxFold tax={result.capitalGainsTax} returnBasis="투자금 기준" />
      )}

      <ResultFold
        title="상세"
        meta={detailMeta(metrics, formatSigned(result.fxReturnPercent, formatPercent))}
      >
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
          <RiskDetail metrics={metrics} other={other} otherLabel={otherLabel} />

          <DetailSection title="투자 정보">
            <DetailRow label="종목" value={result.symbol} />
            <DetailRow label="매수일" value={result.purchaseDate} />
            <DetailRow label="평가일" value={result.currentDate || getTodayString()} />
            <DetailRow label="초기 투자금" value={<>₩{formatNumber(result.investmentAmount)}</>} />
            <div className="flex justify-between gap-3 py-2 border-b border-line/50">
              <span className="text-tx-2">평가 가치</span>
              <div className="text-right">
                <div className="font-medium text-tx-1">₩{formatNumber(result.totalAssetKrw)}</div>
                <div className="text-xs text-tx-2">
                  주식: ₩{formatNumber(result.currentValueKrw)}, 현금: ₩{formatNumber(cashKrw(result))}
                </div>
              </div>
            </div>
            <DetailRow
              label="총 수익"
              value={formatSigned(result.totalReturnKrw, formatKrw)}
              valueClassName={`font-bold ${gainLossClass(result.totalReturnKrw)}`}
              last
            />
          </DetailSection>

          <DetailSection title="주식 수익">
            <DetailRow label="보유 주식수" value={<>{result.shares?.toFixed(2)} 주</>} />
            <DetailRow label="매수 가격" value={<>${result.purchasePrice?.toFixed(2)}</>} />
            <DetailRow label="평가일 가격" value={<>${result.currentPrice?.toFixed(2)}</>} />
            <DetailRow
              label="주가 변동"
              value={formatSigned(result.stockReturn, formatUsd)}
              valueClassName={`font-medium ${gainLossClass(result.stockReturn)}`}
            />
            <DetailRow
              label="주식 수익률"
              value={formatSigned(result.stockReturnPercent, formatPercent)}
              valueClassName={`font-bold ${gainLossClass(result.stockReturnPercent)}`}
            />
            <DetailRow label="배당금 (USD)" value={<>${result.totalDividends?.toFixed(2) || '0.00'}</>} last />
          </DetailSection>

          <FxImpactSection result={result} startLabel="시작일 환율" startRate={result.purchaseFxRate} />

          <DetailSection title="최적 타이밍">
            <DetailRow label="최적 매수일" value={result.optimalBuyDate ?? dash('-')} />
            {result.optimalBuyDate && result.optimalBuyPrice != null && (
              <DetailRow label="최적 매수가" value={<>${result.optimalBuyPrice.toFixed(2)}</>} valueClassName="font-medium text-gain" />
            )}
            <DetailRow label="최적 매도일" value={result.optimalSellDate ?? dash('-')} />
            {result.optimalSellDate && result.optimalSellPrice != null && (
              <DetailRow label="최적 매도가" value={<>${result.optimalSellPrice.toFixed(2)}</>} valueClassName="font-medium text-loss" />
            )}
            <DetailRow
              label="최적 수익률"
              value={result.optimalReturnPercent ? <>+{result.optimalReturnPercent.toFixed(2)}%</> : dash('-')}
              valueClassName={result.optimalReturnPercent ? 'font-bold text-brand' : 'font-medium'}
              last
            />
          </DetailSection>
        </div>
      </ResultFold>
    </div>
  );
};
