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
import { ReturnChart } from './ReturnChart';
import { simpleDeposits } from './assetSeries';

interface SimpleResultProps {
  result: SimulationResponse;
  symbol: string;
  purchaseDate: string;
}

const dash = (value: React.ReactNode) => <span className="text-tx-3">{value}</span>;

// 단순 매수 결과: 카드 · 자산 추이, 그 아래 상세 · 주가 · 수익률 추이를 접어 둠
export const SimpleResult: React.FC<SimpleResultProps> = ({ result, symbol, purchaseDate }) => {
  const [currency, setCurrency] = useState<MetricCurrency>('KRW');
  const { metrics, other, otherLabel } = pickMetrics(result, currency);
  const deposits = useMemo(
    () => simpleDeposits(result.purchaseDate, result.investmentAmount, result.purchaseFxRate),
    [result.purchaseDate, result.investmentAmount, result.purchaseFxRate],
  );

  return (
    <div className="space-y-6">
      <HolidayNotice label="매수일" chosen={purchaseDate} traded={result.purchaseDate} />
      <DefaultFxNotice dates={result.defaultFxDates} />
      <ResultOverview
        result={result}
        investedLabel="초기 투자금"
        invested={result.investmentAmount}
        deposits={deposits}
        currency={currency}
        onCurrencyChange={setCurrency}
        returnRateTestId="backtest-return-rate"
      />

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

      <ResultFold title="주가 · 수익률 추이" meta="기간 고르기 · 그날 기준 수익률">
        <SimpleChart
          symbol={result.symbol || symbol}
          purchaseDate={result.purchaseDate || purchaseDate}
          optimalBuyDate={result.optimalBuyDate ?? undefined}
          optimalSellDate={result.optimalSellDate ?? undefined}
          dividendReinvestDates={result.dividendReinvestDates ?? undefined}
          endDate={result.currentDate ?? undefined}
        />
        <ReturnChart dailyValues={result.dailyValues} deposits={deposits} currency={currency} />
      </ResultFold>
    </div>
  );
};
