import React from 'react';
import { TrendingUp, TrendingDown, DollarSign, Zap, Activity, Repeat } from 'lucide-react';
import { SimpleChart } from '@components/charts/backtest/SimpleChart';
import { gainLossBoxClass, gainLossClass, isGain, signPrefix } from '../../../utils/gainLoss';
import { StatCard } from './StatCard';
import { DetailRow, DetailSection } from './DetailSection';

interface SimpleResultProps {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  result: any;
  symbol: string;
  purchaseDate: string;
}

export const SimpleResult: React.FC<SimpleResultProps> = ({ result, symbol, purchaseDate }) => {
  return (
    <div className="space-y-6">
      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <StatCard
          label="초기 투자금"
          value={<>₩{result.investmentAmount?.toLocaleString()}</>}
          icon={DollarSign}
        />

        <StatCard
          label="현재 가치"
          value={<>₩{result.totalAssetKrw?.toLocaleString()}</>}
          sub={
            <>
              주식: ₩{result.currentValueKrw?.toLocaleString()}, 현금: ₩
              {result.remainingCashKrw?.toLocaleString()}
            </>
          }
          icon={TrendingUp}
        />

        <StatCard
          label="총 수익"
          value={
            <>
              {signPrefix(result.totalReturnKrw)}₩
              {result.totalReturnKrw?.toLocaleString()}
            </>
          }
          valueClassName={gainLossClass(result.totalReturnKrw)}
          icon={isGain(result.totalReturnKrw) ? TrendingUp : TrendingDown}
          iconBoxClassName={gainLossBoxClass(result.totalReturnKrw)}
          iconClassName={gainLossClass(result.totalReturnKrw)}
        />

        <StatCard
          label="수익률"
          value={
            <>
              {signPrefix(result.totalReturnPercent)}
              {result.totalReturnPercent?.toFixed(2)}%
            </>
          }
          valueClassName={gainLossClass(result.totalReturnPercent)}
          icon={Activity}
          iconBoxClassName={gainLossBoxClass(result.totalReturnPercent)}
          iconClassName={gainLossClass(result.totalReturnPercent)}
          testId="backtest-return-rate"
        />
      </div>

      {/* Detailed Results - 4 Sections: Investment, Stock Performance, FX Impact, Optimal Timing */}
      <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-4 gap-6">
        <DetailSection title="투자 정보" icon={DollarSign}>
          <DetailRow label="종목" value={result.symbol} />
          <DetailRow label="매수일" value={result.purchaseDate} />
          <DetailRow
            label="평가일"
            value={result.currentDate || new Date().toISOString().split("T")[0]}
          />
          <DetailRow
            label="초기 투자금"
            value={<>₩{result.investmentAmount?.toLocaleString()}</>}
          />
          <div className="flex justify-between py-2 border-b border-line/50">
            <span className="text-tx-2">현재 가치</span>
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
              <>
                {signPrefix(result.totalReturnKrw)}₩
                {result.totalReturnKrw?.toLocaleString()}
              </>
            }
            valueClassName={`font-bold ${gainLossClass(result.totalReturnKrw)}`}
            last
          />
        </DetailSection>

        <DetailSection title="주식 수익" icon={TrendingUp} iconClassName="text-green-600">
          <DetailRow label="보유 주식수" value={<>{result.shares?.toFixed(6)} 주</>} />
          <DetailRow label="매수 가격" value={<>${result.purchasePrice?.toFixed(2)}</>} />
          <DetailRow label="현재 가격" value={<>${result.currentPrice?.toFixed(2)}</>} />
          <DetailRow
            label="주가 변동"
            value={
              <>
                {signPrefix(result.stockReturn)}$
                {result.stockReturn?.toFixed(2)}
              </>
            }
            valueClassName={`font-medium ${gainLossClass(result.stockReturn)}`}
          />
          <DetailRow
            label="주식 수익률"
            value={
              <>
                {signPrefix(result.stockReturnPercent)}
                {result.stockReturnPercent?.toFixed(2)}%
              </>
            }
            valueClassName={`font-bold ${gainLossClass(result.stockReturnPercent)}`}
          />
          <DetailRow
            label="배당금 (USD)"
            value={<>${result.totalDividends?.toFixed(2) || "0.00"}</>}
            last
          />
        </DetailSection>

        <DetailSection title="환율 영향" icon={Repeat} borderClassName="border-brand/25">
          <DetailRow
            label="시작일 환율"
            value={<>₩{result.purchaseFxRate?.toLocaleString()}</>}
            dividerClassName="border-line"
          />
          <DetailRow
            label="현재 환율"
            value={<>₩{result.currentFxRate?.toLocaleString()}</>}
            dividerClassName="border-line"
          />
          <DetailRow
            label="환율 변동"
            value={
              <>
                {signPrefix(result.fxReturn)}₩
                {result.fxReturn?.toFixed(2)}
              </>
            }
            valueClassName={`font-medium ${gainLossClass(result.fxReturn)}`}
            dividerClassName="border-line"
          />
          <DetailRow
            label="환차익률"
            value={
              <>
                {signPrefix(result.fxReturnPercent)}
                {result.fxReturnPercent?.toFixed(2)}%
              </>
            }
            valueClassName={`font-bold ${gainLossClass(result.fxReturnPercent)}`}
            last
          />
        </DetailSection>

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
                  valueClassName="font-medium text-green-600"
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
                  valueClassName="font-medium text-red-600"
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
        optimalBuyDate={result.optimalBuyDate}
        optimalSellDate={result.optimalSellDate}
        dividendReinvestDates={result.dividendReinvestDates}
      />
    </div>
  );
};
