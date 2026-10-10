import React from 'react';
import { TrendingUp, DollarSign, Zap } from 'lucide-react';
import { SimpleChart } from '@components/charts/backtest/SimpleChart';
import type { SimulationResponse } from '../../../services/api';
import { formatKrw, formatPercent, formatSigned, formatUsd, gainLossClass } from '../../../utils/gainLoss';
import { SummaryCards } from './SummaryCards';
import { FxImpactSection } from './FxImpactSection';
import { DetailRow, DetailSection } from './DetailSection';
import { getTodayString } from '../../../utils/dateUtils';

interface SimpleResultProps {
  result: SimulationResponse;
  symbol: string;
  purchaseDate: string;
}

export const SimpleResult: React.FC<SimpleResultProps> = ({ result, symbol, purchaseDate }) => {
  return (
    <div className="space-y-6">
      <SummaryCards
        result={result}
        investedLabel="초기 투자금"
        invested={result.investmentAmount}
        returnRateTestId="backtest-return-rate"
      />

      {/* Detailed Results - 4 Sections: Investment, Stock Performance, FX Impact, Optimal Timing */}
      <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-4 gap-6">
        <DetailSection title="투자 정보" icon={DollarSign}>
          <DetailRow label="종목" value={result.symbol} />
          <DetailRow label="매수일" value={result.purchaseDate} />
          <DetailRow
            label="평가일"
            value={result.currentDate || getTodayString()}
          />
          <DetailRow
            label="초기 투자금"
            value={<>₩{result.investmentAmount?.toLocaleString()}</>}
          />
          <div className="flex justify-between py-2 border-b border-line/50">
            <span className="text-tx-2">평가 가치</span>
            <div className="text-right">
              <div className="font-medium text-tx-1">
                ₩{result.totalAssetKrw?.toLocaleString()}
              </div>
              <div className="text-xs text-tx-2">
                주식: ₩{result.currentValueKrw?.toLocaleString()}, 현금: ₩
                {result.remainingCashKrw?.toLocaleString()}
              </div>
            </div>
          </div>
          <DetailRow
            label="총 수익"
            value={
              formatSigned(result.totalReturnKrw, formatKrw)
            }
            valueClassName={`font-bold ${gainLossClass(result.totalReturnKrw)}`}
            last
          />
        </DetailSection>

        <DetailSection title="주식 수익" icon={TrendingUp} iconClassName="text-gain">
          <DetailRow label="보유 주식수" value={<>{result.shares?.toFixed(6)} 주</>} />
          <DetailRow label="매수 가격" value={<>${result.purchasePrice?.toFixed(2)}</>} />
          <DetailRow label="평가일 가격" value={<>${result.currentPrice?.toFixed(2)}</>} />
          <DetailRow
            label="주가 변동"
            value={
              formatSigned(result.stockReturn, formatUsd)
            }
            valueClassName={`font-medium ${gainLossClass(result.stockReturn)}`}
          />
          <DetailRow
            label="주식 수익률"
            value={
              formatSigned(result.stockReturnPercent, formatPercent)
            }
            valueClassName={`font-bold ${gainLossClass(result.stockReturnPercent)}`}
          />
          <DetailRow
            label="배당금 (USD)"
            value={<>${result.totalDividends?.toFixed(2) || "0.00"}</>}
            last
          />
        </DetailSection>

        <FxImpactSection result={result} startLabel="시작일 환율" startRate={result.purchaseFxRate} />

        <DetailSection
          title="최적 타이밍"
          icon={Zap}
          borderClassName="border-brand/25"
          className="bg-brand-bg"
        >
          {result.optimalBuyDate ? (
            <>
              <DetailRow
                label="최적 매수일"
                value={result.optimalBuyDate}
                dividerClassName="border-brand/25"
              />
              {result.optimalBuyPrice && (
                <DetailRow
                  label="최적 매수가"
                  value={<>${result.optimalBuyPrice?.toFixed(2)}</>}
                  valueClassName="font-medium text-gain"
                  dividerClassName="border-brand/25"
                />
              )}
            </>
          ) : (
            <DetailRow
              label="최적 매수일"
              value="-"
              valueClassName="font-medium text-tx-3"
              dividerClassName="border-brand/25"
            />
          )}
          {result.optimalSellDate ? (
            <>
              <DetailRow
                label="최적 매도일"
                value={result.optimalSellDate}
                dividerClassName="border-brand/25"
              />
              {result.optimalSellPrice && (
                <DetailRow
                  label="최적 매도가"
                  value={<>${result.optimalSellPrice?.toFixed(2)}</>}
                  valueClassName="font-medium text-loss"
                  dividerClassName="border-brand/25"
                />
              )}
            </>
          ) : (
            <DetailRow
              label="최적 매도일"
              value="-"
              valueClassName="font-medium text-tx-3"
              dividerClassName="border-brand/25"
            />
          )}
          {result.optimalReturnPercent ? (
            <DetailRow
              label="최적 수익률"
              value={<>+{result.optimalReturnPercent?.toFixed(2)}%</>}
              valueClassName="font-bold text-brand"
              last
            />
          ) : (
            <DetailRow label="최적 수익률" value="-" valueClassName="font-medium text-tx-3" last />
          )}
        </DetailSection>
      </div>

      {/* 차트 섹션 추가 - Simple */}
      <SimpleChart
        symbol={result.symbol || symbol}
        purchaseDate={result.purchaseDate || purchaseDate}
        shares={result.shares || 0}
        investmentAmount={result.investmentAmount || 0}
        optimalBuyDate={result.optimalBuyDate ?? undefined}
        optimalSellDate={result.optimalSellDate ?? undefined}
        dividendReinvestDates={result.dividendReinvestDates ?? undefined}
        endDate={result.currentDate ?? undefined}
      />
    </div>
  );
};
