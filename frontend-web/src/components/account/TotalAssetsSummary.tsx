import React from 'react';
import { Wallet, DollarSign } from 'lucide-react';

/**
 * 총 자산 요약 컴포넌트의 Props
 */
export interface TotalAssetsSummaryProps {
  /** 총 자산 금액 (KRW) */
  totalAssets: number;
  /** 계좌 개수 */
  accountCount: number;
  /** 현재 환율 (KRW/USD) */
  currentExchangeRate: number;
  /** 잔액 숨김 여부 */
  hideBalances: boolean;
  /** 잔액 포맷팅 함수 */
  formatBalance: (amount: number, currency: 'KRW' | 'USD') => string;
}

/**
 * 총 자산 요약 컴포넌트
 *
 * @description 모든 계좌의 총 자산과 현재 환율 정보를 요약하여 표시합니다
 */
export const TotalAssetsSummary: React.FC<TotalAssetsSummaryProps> = ({
  totalAssets,
  accountCount,
  currentExchangeRate,
  hideBalances,
  formatBalance
}) => {
  return (
    <div className="bg-indigo-600 rounded-xl shadow-sm p-6 text-white">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-indigo-100 text-sm font-medium">총 자산</p>
          <p className="text-3xl font-bold">{formatBalance(totalAssets, 'KRW')}</p>
          <p className="text-indigo-100 text-sm mt-1">{accountCount}개 계좌</p>
        </div>
        <div className="bg-white/20 p-3 rounded-lg">
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
