import React from 'react';
import { TrendingUp, TrendingDown, DollarSign, Activity, Repeat } from 'lucide-react';
import { DCAChart } from '@components/charts/backtest/DCAChart';
import { ConditionalChart } from '@components/charts/backtest/ConditionalChart';
import type { StrategyResponse } from '../../../services/api';
import type { BacktestMode } from '../shared/backtestDisplay';
import {
  formatKrw,
  formatPercent,
  formatSigned,
  gainLossBoxClass,
  gainLossClass,
  gainLossTone,
} from '../../../utils/gainLoss';
import { StatCard } from './StatCard';
import { DetailRow, DetailSection } from './DetailSection';

interface DcaConditionalResultProps {
  result: StrategyResponse;
  mode: BacktestMode;
  symbol: string;
  dcaStartDate: string;
  conditionalStartDate: string;
}

export const DcaConditionalResult: React.FC<DcaConditionalResultProps> = ({
  result,
  mode,
  symbol,
  dcaStartDate,
  conditionalStartDate,
}) => {
  return (
    <div className="space-y-6">
      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <StatCard
          label="총 투자금"
          value={<>₩{result.totalInvested?.toLocaleString()}</>}
          icon={DollarSign}
        />

        <StatCard
          label="평가 가치"
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
            formatSigned(result.totalReturnKrw, formatKrw)
          }
          valueClassName={gainLossClass(result.totalReturnKrw)}
          icon={gainLossTone(result.totalReturnKrw) === "loss" ? TrendingDown : TrendingUp}
          iconBoxClassName={gainLossBoxClass(result.totalReturnKrw)}
          iconClassName={gainLossClass(result.totalReturnKrw)}
        />

        <StatCard
          label="수익률"
          value={
            formatSigned(result.totalReturnPercent, formatPercent)
          }
          valueClassName={gainLossClass(result.totalReturnPercent)}
          icon={Activity}
          iconBoxClassName={gainLossBoxClass(result.totalReturnPercent)}
          iconClassName={gainLossClass(result.totalReturnPercent)}
        />
      </div>

      {/* Detailed Results - 3 Columns like Simple */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <DetailSection title="투자 정보" icon={DollarSign}>
          <DetailRow label="종목" value={result.symbol} />
          <DetailRow
            label="기간"
            value={
              <>
                {result.startDate?.toString().substring(0, 7)} ~{" "}
                {result.endDate?.toString().substring(0, 7)}
              </>
            }
          />
          <DetailRow label="거래 횟수" value={<>{result.totalTransactions}회</>} />
          <DetailRow
            label="총 투자금"
            value={<>₩{result.totalInvested?.toLocaleString()}</>}
          />
          <div className="flex justify-between py-2">
            <span className="text-tx-2">평가 가치</span>
            <div className="text-right">
              <div className="font-medium text-tx-1">
                ₩{result.currentValueKrw?.toLocaleString()}
              </div>
              <div className="text-xs text-tx-2">
                ${result.currentValue?.toLocaleString()}
              </div>
            </div>
          </div>
        </DetailSection>

        <DetailSection title="주식 수익" icon={TrendingUp} iconClassName="text-gain">
          <DetailRow label="보유 주식수" value={<>{result.totalShares?.toFixed(6)} 주</>} />
          <DetailRow label="평균 매수가" value={<>${result.averagePrice?.toFixed(2)}</>} />
          <DetailRow label="평가일 가격" value={<>${result.currentPrice?.toFixed(2)}</>} />
          <DetailRow
            label="배당금 (USD)"
            value={<>${result.totalDividends?.toFixed(2) || "0.00"}</>}
          />
          <DetailRow
            label="총 수익 (KRW)"
            value={
              formatSigned(result.totalReturnKrw, formatKrw)
            }
            valueClassName={`font-bold ${gainLossClass(result.totalReturnKrw)}`}
          />
          <DetailRow
            label="수익률"
            value={
              formatSigned(result.totalReturnPercent, formatPercent)
            }
            valueClassName={`font-bold ${gainLossClass(result.totalReturnPercent)}`}
            last
          />
        </DetailSection>

        <DetailSection title="환율 영향" icon={Repeat} borderClassName="border-brand/25">
          <DetailRow
            label="평균 환율"
            value={<>₩{result.averageFxRate?.toLocaleString()}</>}
            dividerClassName="border-line"
          />
          <DetailRow
            label="평가일 환율"
            value={<>₩{result.currentFxRate?.toLocaleString()}</>}
            dividerClassName="border-line"
          />
          <DetailRow
            label="환율 변동"
            value={
              formatSigned(result.fxReturn, (v) => `₩${v.toFixed(2)}`)
            }
            valueClassName={`font-medium ${gainLossClass(result.fxReturn)}`}
            dividerClassName="border-line"
          />
          <DetailRow
            label="환차익률"
            value={
              formatSigned(result.fxReturnPercent, formatPercent)
            }
            valueClassName={`font-bold ${gainLossClass(result.fxReturnPercent)}`}
            last
          />
        </DetailSection>
      </div>

      {/* 차트 섹션 추가 */}
      {result.transactions &&
        result.transactions.length > 0 &&
        (mode === "dca" ? (
          <DCAChart
            symbol={result.symbol || symbol}
            transactions={result.transactions}
            currentValueKrw={result.currentValueKrw || 0}
            totalInvested={result.totalInvested || 0}
            startDate={result.startDate || dcaStartDate}
          />
        ) : (
          <ConditionalChart
            symbol={result.symbol || symbol}
            transactions={result.transactions}
            currentValueKrw={result.currentValueKrw || 0}
            totalInvested={result.totalInvested || 0}
            startDate={result.startDate || conditionalStartDate}
          />
        ))}
    </div>
  );
};
