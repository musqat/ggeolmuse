import React from 'react';
import { TrendingUp, TrendingDown, DollarSign, ArrowUpCircle, ArrowDownCircle } from 'lucide-react';
import type { PortfolioSummaryResponse } from '../../services/api';

interface SummaryCardsProps {
  totalAssets: number;
  summary: PortfolioSummaryResponse | null | undefined;
}

// 총 자산 · 평가손익 · 수익률 카드
export const SummaryCards: React.FC<SummaryCardsProps> = ({ totalAssets, summary }) => {
  const pnl = summary?.totalUnrealizedPnL ?? 0;
  const returnRate = summary?.totalReturnRate ?? 0;

  return (
    <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
      <div className="bg-surface rounded-xl shadow-sm p-6 border border-line/50">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-tx-2">총 자산</p>
            <p className="text-2xl font-bold text-tx-1">${totalAssets.toFixed(2)}</p>
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
            <p className={`text-2xl font-bold ${pnl >= 0 ? 'text-gain' : 'text-loss'}`}>
              {pnl >= 0 ? '+' : ''}${pnl.toFixed(2)}
            </p>
          </div>
          <div className={`p-3 rounded-lg ${pnl >= 0 ? 'bg-gain/15' : 'bg-loss/15'}`}>
            {pnl >= 0 ? (
              <TrendingUp className="w-6 h-6 text-gain" />
            ) : (
              <TrendingDown className="w-6 h-6 text-loss" />
            )}
          </div>
        </div>
      </div>

      <div className="bg-surface rounded-xl shadow-sm p-6 border border-line/50">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-tx-2">수익률</p>
            <p className={`text-2xl font-bold ${returnRate >= 0 ? 'text-gain' : 'text-loss'}`}>
              {returnRate >= 0 ? '+' : ''}{returnRate.toFixed(2)}%
            </p>
          </div>
          <div className={`p-3 rounded-lg ${returnRate >= 0 ? 'bg-gain/15' : 'bg-loss/15'}`}>
            {returnRate >= 0 ? (
              <ArrowUpCircle className="w-6 h-6 text-gain" />
            ) : (
              <ArrowDownCircle className="w-6 h-6 text-loss" />
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
