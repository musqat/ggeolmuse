import React from 'react';
import { DollarSign } from 'lucide-react';
import { formatFxRate } from '../../utils/format';

export interface TotalAssetsSummaryProps {
  totalAssets: number;
  accountCount: number;
  currentExchangeRate: number;
  formatBalance: (amount: number, currency: 'KRW' | 'USD') => string;
  // 주식 평가액을 못 받아 총자산이 현금만이면 안내
  stocksUnavailable?: boolean;
}

/**
 * 총 자산 요약 컴포넌트
 */
export const TotalAssetsSummary: React.FC<TotalAssetsSummaryProps> = ({
  totalAssets,
  accountCount,
  currentExchangeRate,
  formatBalance,
  stocksUnavailable = false
}) => {
  return (
    <div className="bg-brand rounded-xl shadow-sm p-6 text-brand-ink">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-brand-ink/90 text-sm font-medium">총 자산</p>
          <p className="text-3xl font-bold">{formatBalance(totalAssets, 'KRW')}</p>
          <p className="text-brand-ink/90 text-sm mt-1">{accountCount}개 계좌</p>
          {stocksUnavailable && (
            <p className="text-brand-ink/90 text-xs mt-1">주식 평가액을 불러오지 못해 현금만 셌습니다</p>
          )}
        </div>
      </div>
      {currentExchangeRate > 0 && (
        <div className="mt-4 pt-4 border-t border-brand-ink/20">
          <div className="flex items-center space-x-2 text-sm">
            <DollarSign className="w-4 h-4" />
            <span>현재 환율: {formatFxRate(currentExchangeRate)}/USD</span>
          </div>
        </div>
      )}
    </div>
  );
};
