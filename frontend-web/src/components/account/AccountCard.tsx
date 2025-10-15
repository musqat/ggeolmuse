import React from 'react';
import { useNavigate } from 'react-router-dom';
import { TrendingUp, ArrowDownRight, RefreshCw, Trash2, Wallet } from 'lucide-react';
import type { AccountSummary, AccountBalance } from '../../services/api';

/**
 * 계좌 카드 컴포넌트의 Props
 */
export interface AccountCardProps {
  /** 계좌 정보 */
  account: AccountSummary;
  /** 계좌 잔액 정보 */
  balance: AccountBalance;
  /** 잔액 숨김 여부 */
  hideBalances: boolean;
  /** 잔액 포맷팅 함수 */
  formatBalance: (amount: number, currency: 'KRW' | 'USD') => string;
  /** 입금 버튼 클릭 핸들러 */
  onDeposit: (accountId: number) => void;
  /** 환전 버튼 클릭 핸들러 */
  onExchange: (accountId: number) => void;
  /** 삭제 버튼 클릭 핸들러 */
  onDelete: (accountId: number) => void;
}

export const AccountCard: React.FC<AccountCardProps> = ({
  account,
  balance,
  hideBalances,
  formatBalance,
  onDeposit,
  onExchange,
  onDelete
}) => {
  const navigate = useNavigate();

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 hover:shadow-md transition-shadow">
      {/* 계좌 헤더 */}
      <div className="flex items-start justify-between mb-4">
        <div className="flex items-center space-x-3">
          <div className="bg-indigo-100 p-2 rounded-lg">
            <TrendingUp className="w-5 h-5 text-indigo-600" />
          </div>
          <div>
            <h3 className="font-semibold text-gray-900">{account.accountName}</h3>
            <p className="text-sm text-gray-500">계좌 #{account.accountId}</p>
          </div>
        </div>
      </div>

      {/* 잔액 정보 */}
      <div className="space-y-3">
        <div>
          <p className="text-sm text-gray-500">KRW 잔액</p>
          <p className="text-xl font-bold text-gray-900">
            {formatBalance(balance.balanceKrw, 'KRW')}
          </p>
        </div>

        <div>
          <p className="text-sm text-gray-500">USD 잔액</p>
          <p className="text-xl font-bold text-gray-900">
            {formatBalance(balance.balanceUsd, 'USD')}
          </p>
        </div>

        {/* 수수료 정보 */}
        <div className="flex items-center justify-between text-sm pt-3 border-t">
          <span className="text-gray-500">거래 수수료</span>
          <span className="font-medium">{(account.commissionRate * 100).toFixed(2)}%</span>
        </div>

        {/* 액션 버튼 그룹 */}
        <div className="grid grid-cols-3 gap-2 pt-3 border-t">
          <button
            onClick={() => onDeposit(account.accountId)}
            className="w-full flex items-center justify-center space-x-1 py-2 px-2 bg-green-50 text-green-700 rounded-lg hover:bg-green-100 transition-colors"
          >
            <ArrowDownRight className="w-4 h-4" />
            <span className="text-sm font-medium">입금</span>
          </button>
          <button
            onClick={() => onExchange(account.accountId)}
            className="w-full flex items-center justify-center space-x-1 py-2 px-2 bg-blue-50 text-blue-700 rounded-lg hover:bg-blue-100 transition-colors"
          >
            <RefreshCw className="w-4 h-4" />
            <span className="text-sm font-medium">환전</span>
          </button>
          <button
            onClick={() => onDelete(account.accountId)}
            className="w-full flex items-center justify-center space-x-1 py-2 px-2 bg-red-50 text-red-700 rounded-lg hover:bg-red-100 transition-colors"
          >
            <Trash2 className="w-4 h-4" />
            <span className="text-sm font-medium">삭제</span>
          </button>
        </div>

        {/* 포트폴리오 확인 버튼 */}
        <button
          onClick={() => navigate(`/portfolio?accountId=${account.accountId}`)}
          className="w-full flex items-center justify-center space-x-2 py-2 px-3 bg-indigo-50 text-indigo-700 rounded-lg hover:bg-indigo-100 transition-colors mt-2"
        >
          <Wallet className="w-4 h-4" />
          <span className="text-sm font-medium">포트폴리오 확인</span>
        </button>
      </div>
    </div>
  );
};
