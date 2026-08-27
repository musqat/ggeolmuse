import React from 'react';
import { useNavigate } from 'react-router-dom';
import { TrendingUp, ArrowDownRight, RefreshCw, Trash2, Wallet } from 'lucide-react';
import type { AccountSummary, AccountBalance } from '../../services/api';

export interface AccountCardProps {
  account: AccountSummary;
  balance: AccountBalance;
  formatBalance: (amount: number, currency: 'KRW' | 'USD') => string;
  onDeposit: (accountId: number) => void;
  onExchange: (accountId: number) => void;
  onDelete: (accountId: number) => void;
}

export const AccountCard: React.FC<AccountCardProps> = ({
  account,
  balance,
  formatBalance,
  onDeposit,
  onExchange,
  onDelete
}) => {
  const navigate = useNavigate();

  return (
    <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6 hover:shadow-md transition-shadow">
      {/* 계좌 헤더 */}
      <div className="flex items-start justify-between mb-4">
        <div className="flex items-center space-x-3">
          <div className="bg-brand-bg p-2 rounded-lg">
            <TrendingUp className="w-5 h-5 text-brand" />
          </div>
          <div>
            <h3 className="font-semibold text-tx-1">{account.accountName}</h3>
            <p className="text-sm text-tx-2">계좌 #{account.accountId}</p>
          </div>
        </div>
      </div>

      {/* 잔액 정보 */}
      <div className="space-y-3">
        <div>
          <p className="text-sm text-tx-2">KRW 잔액</p>
          <p className="text-xl font-bold text-tx-1">
            {formatBalance(balance.balanceKrw, 'KRW')}
          </p>
        </div>

        <div>
          <p className="text-sm text-tx-2">USD 잔액</p>
          <p className="text-xl font-bold text-tx-1">
            {formatBalance(balance.balanceUsd, 'USD')}
          </p>
        </div>

        {/* 수수료 정보 */}
        <div className="flex items-center justify-between text-sm pt-3 border-t">
          <span className="text-tx-2">거래 수수료</span>
          <span className="font-medium">{(account.commissionRate * 100).toFixed(2)}%</span>
        </div>

        {/* 액션 버튼 그룹 */}
        <div className="grid grid-cols-3 gap-2 pt-3 border-t">
          <button
            onClick={() => onDeposit(account.accountId)}
            className="w-full flex items-center justify-center space-x-1 py-2 px-2 bg-green-500/10 text-green-600 rounded-lg hover:bg-green-500/100/15 transition-colors"
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
            className="w-full flex items-center justify-center space-x-1 py-2 px-2 bg-red-500/10 text-red-600 rounded-lg hover:bg-red-500/100/15 transition-colors"
          >
            <Trash2 className="w-4 h-4" />
            <span className="text-sm font-medium">삭제</span>
          </button>
        </div>

        {/* 포트폴리오 확인 버튼 */}
        <button
          onClick={() => navigate(`/portfolio?accountId=${account.accountId}`)}
          className="w-full flex items-center justify-center space-x-2 py-2 px-3 bg-brand-bg text-brand-dark rounded-lg hover:bg-brand-bg transition-colors mt-2"
        >
          <Wallet className="w-4 h-4" />
          <span className="text-sm font-medium">포트폴리오 확인</span>
        </button>
      </div>
    </div>
  );
};
