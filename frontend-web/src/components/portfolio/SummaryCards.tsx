import React from 'react';
import { TrendingUp, TrendingDown, DollarSign, ArrowUpCircle, ArrowDownCircle } from 'lucide-react';
import type { PortfolioSummaryResponse } from '../../services/api';
import { formatPercent, formatSigned, formatUsd } from '../../utils/format';
import { gainLossTone } from '../../utils/gainLoss';

interface SummaryCardsProps {
  totalAssets: number;
  summary: PortfolioSummaryResponse | null | undefined;
}

// 총 자산 · 평가손익 · 수익률 카드
export const SummaryCards: React.FC<SummaryCardsProps> = ({ totalAssets, summary }) => {
  const pnl = summary?.totalUnrealizedPnL ?? 0;
  const returnRate = summary?.totalReturnRate ?? 0;
  // 표시 자릿수로 반올림해 손실인지(0.00 은 손실 아님)
  const pnlDown = gainLossTone(pnl) === 'loss';
  const returnDown = gainLossTone(returnRate) === 'loss';

  return (
    <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
      <div className="bg-surface rounded-xl shadow-sm p-6 border border-line/50">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-tx-2">총 자산 (달러 환산)</p>
            <p className="text-2xl font-bold text-tx-1">{formatUsd(totalAssets)}</p>
          </div>
          <div className="bg-info/10 p-3 rounded-lg">
            <DollarSign className="w-6 h-6 text-info" />
          </div>
        </div>
      </div>

      <div className="bg-surface rounded-xl shadow-sm p-6 border border-line/50">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-tx-2">평가손익</p>
            <p className={`text-2xl font-bold ${pnlDown ? 'text-loss' : 'text-gain'}`}>
              {formatSigned(pnl, formatUsd)}
            </p>
          </div>
          <div className={`p-3 rounded-lg ${pnlDown ? 'bg-loss/15' : 'bg-gain/15'}`}>
            {pnlDown ? (
              <TrendingDown className="w-6 h-6 text-loss" />
            ) : (
              <TrendingUp className="w-6 h-6 text-gain" />
            )}
          </div>
        </div>
      </div>

      <div className="bg-surface rounded-xl shadow-sm p-6 border border-line/50">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-tx-2">수익률</p>
            <p className={`text-2xl font-bold ${returnDown ? 'text-loss' : 'text-gain'}`}>
              {formatSigned(returnRate, formatPercent)}
            </p>
          </div>
          <div className={`p-3 rounded-lg ${returnDown ? 'bg-loss/15' : 'bg-gain/15'}`}>
            {returnDown ? (
              <ArrowDownCircle className="w-6 h-6 text-loss" />
            ) : (
              <ArrowUpCircle className="w-6 h-6 text-gain" />
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
