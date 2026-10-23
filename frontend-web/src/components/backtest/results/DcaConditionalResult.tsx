import React from 'react';
import { TrendingUp, DollarSign } from 'lucide-react';
import { AccumulationChart } from '@components/charts/backtest/AccumulationChart';
import type { StrategyResponse } from '../../../services/api';
import { formatBalanceAmount, formatKrw, formatPercent, formatSigned, gainLossClass } from '../../../utils/gainLoss';
import { SummaryCards } from './SummaryCards';
import { FxImpactSection } from './FxImpactSection';
import { DetailRow, DetailSection } from './DetailSection';

interface DcaConditionalResultProps {
  result: StrategyResponse;
  symbol: string;
  // 결과에 시작일이 없을 때 차트 시작일
  startDate: string;
}

export const DcaConditionalResult: React.FC<DcaConditionalResultProps> = ({
  result,
  symbol,
  startDate,
}) => {
  // 투자일이 휴장일이라 다른 날 산 번수
  const moved = result.transactions?.filter((tx) => tx.actualDate && tx.actualDate !== tx.date).length ?? 0;

  return (
    <div className="space-y-6">
      {moved > 0 && (
        <p className="text-sm text-warning bg-warning-soft/10 border border-warning-soft/40 rounded-lg px-3 py-2">
          투자일이 휴장일이면 다음 거래일 종가로 삽니다(이번 결과 {moved}번).
        </p>
      )}
      <SummaryCards result={result} investedLabel="총 투자금" invested={result.totalInvested} />

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
            <span className="text-tx-2">주식 평가액</span>
            <div className="text-right">
              <div className="font-medium text-tx-1">
                ₩{result.currentValueKrw?.toLocaleString()}
              </div>
              <div className="text-xs text-tx-2">
                {result.currentValue != null && formatBalanceAmount(result.currentValue, "USD")}
              </div>
            </div>
          </div>
        </DetailSection>

        <DetailSection title="주식 수익" icon={TrendingUp} iconClassName="text-gain">
          <DetailRow label="보유 주식수" value={<>{result.totalShares?.toFixed(2)} 주</>} />
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

        <FxImpactSection result={result} startLabel="평균 환율" startRate={result.averageFxRate} />
      </div>

      {/* 차트 섹션 추가 */}
      {result.transactions && result.transactions.length > 0 && (
        <AccumulationChart
          symbol={result.symbol || symbol}
          transactions={result.transactions}
          currentValueKrw={result.currentValueKrw || 0}
          totalInvested={result.totalInvested || 0}
          startDate={result.startDate || startDate}
          endDate={result.endDate ?? undefined}
        />
      )}
    </div>
  );
};
