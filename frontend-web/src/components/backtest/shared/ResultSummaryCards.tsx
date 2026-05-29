import React from 'react';
import { DollarSign, TrendingUp, TrendingDown, Wallet } from 'lucide-react';

interface ResultSummaryCardsProps {
  investmentAmount: number;
  currentValueKrw: number;
  remainingCashKrw?: number;
  totalReturnKrw: number;
  totalReturnPercent: number;
  totalInvested?: number;
  showRemainingCash?: boolean;
}

export const ResultSummaryCards: React.FC<ResultSummaryCardsProps> = ({
  investmentAmount,
  currentValueKrw,
  remainingCashKrw,
  totalReturnKrw,
  totalReturnPercent,
  totalInvested,
  showRemainingCash = false,
}) => {
  const isProfit = totalReturnKrw >= 0;
  const displayInvestment = totalInvested ?? investmentAmount;

  return (
    <div className={`grid grid-cols-1 ${showRemainingCash ? 'md:grid-cols-4':'md:grid-cols-3'} gap-4`}>
      {/* 투자 금액 */}
      <div className="bg-surface border border-line rounded-lg p-4">
        <div className="flex items-center justify-between mb-2">
          <span className="text-sm text-tx-2">투자 금액</span>
          <DollarSign className="w-4 h-4 text-brand/600" />
        </div>
        <p className="text-xl font-bold text-tx-1">
          ₩{displayInvestment.toLocaleString()}
        </p>
        {totalInvested && totalInvested !== investmentAmount && (
          <p className="text-xs text-tx-2 mt-1">
            초기 예산: ₩{investmentAmount.toLocaleString()}
          </p>
        )}
      </div>

      {/* 현재 가치 */}
      <div className="bg-surface border border-line rounded-lg p-4">
        <div className="flex items-center justify-between mb-2">
          <span className="text-sm text-tx-2">현재 가치</span>
          <TrendingUp className="w-4 h-4 text-green-600" />
        </div>
        <p className="text-xl font-bold text-tx-1">
          ₩{currentValueKrw.toLocaleString()}
        </p>
      </div>

      {/* 잔여 현금 (DCA/Conditional에서만 표시) */}
      {showRemainingCash && remainingCashKrw !== undefined && (
        <div className="bg-surface border border-line rounded-lg p-4">
          <div className="flex items-center justify-between mb-2">
            <span className="text-sm text-tx-2">잔여 현금</span>
            <Wallet className="w-4 h-4 text-purple-600" />
          </div>
          <p className="text-xl font-bold text-tx-1">
            ₩{remainingCashKrw.toLocaleString()}
          </p>
        </div>
      )}

      {/* 총 수익 */}
      <div className={`bg-surface border ${isProfit ? 'border-green-500/25':'border-red-500/25'} rounded-lg p-4`}>
        <div className="flex items-center justify-between mb-2">
          <span className="text-sm text-tx-2">총 수익</span>
          {isProfit ? (
            <TrendingUp className="w-4 h-4 text-green-600" />
          ) : (
            <TrendingDown className="w-4 h-4 text-red-600" />
          )}
        </div>
        <p className={`text-xl font-bold ${isProfit ? 'text-green-600':'text-red-600'}`}>
          {isProfit ? '+' : ''}₩{totalReturnKrw.toLocaleString()}
        </p>
        <p className={`text-sm font-medium mt-1 ${isProfit ? 'text-green-600':'text-red-600'}`}>
          {isProfit ? '+' : ''}{totalReturnPercent.toFixed(2)}%
        </p>
      </div>
    </div>
  );
};
