import React from 'react';
import { Wallet, DollarSign } from 'lucide-react';

export interface TotalAssetsSummaryProps {
  totalAssets: number;
  accountCount: number;
  currentExchangeRate: number;
  hideBalances: boolean;
  formatBalance: (amount: number, currency: 'KRW' | 'USD') => string;
}

/**
 * 총 자산 요약 컴포넌트
 */
export const TotalAssetsSummary: React.FC<TotalAssetsSummaryProps> = ({
  totalAssets,
  accountCount,
  currentExchangeRate,
  hideBalances,
  formatBalance
}) => {
  return (
    <div className="bg-brand rounded-xl shadow-sm p-6 text-white">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-indigo-100 text-sm font-medium">총 자산</p>
          <p className="text-3xl font-bold">{formatBalance(totalAssets, 'KRW')}</p>
          <p className="text-indigo-100 text-sm mt-1">{accountCount}개 계좌</p>
        </div>
        <div className="bg-surface/20 p-3 rounded-lg">
          <Wallet className="w-8 h-8" />
        </div>
      </div>
      {currentExchangeRate > 0 && (
        <div className="mt-4 pt-4 border-t border-indigo-400">
          <div className="flex items-center space-x-2 text-sm">
            <DollarSign className="w-4 h-4" />
            <span>현재 환율: ₩{currentExchangeRate.toLocaleString()}/USD</span>
          </div>
        </div>
      )}
    </div>
  );
};
