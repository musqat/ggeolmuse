import React from 'react';
import { Plus, Eye, EyeOff } from 'lucide-react';

export interface AccountPageHeaderProps {
  hideBalances: boolean;
  onToggleBalances: () => void;
  onCreateAccount: () => void;
}

export const AccountPageHeader: React.FC<AccountPageHeaderProps> = ({
  hideBalances,
  onToggleBalances,
  onCreateAccount
}) => {
  return (
    <div className="flex flex-col md:flex-row md:items-center md:justify-between">
      <div>
        <h1 className="text-3xl font-bold text-tx-1">계좌 관리</h1>
        <p className="text-tx-2 mt-1">투자 계좌를 관리하세요</p>
      </div>
      <div className="flex items-center space-x-3 mt-4 md:mt-0">
        <button
          onClick={onToggleBalances}
          className="flex items-center space-x-2 px-4 py-2 bg-elevated text-tx-1 rounded-lg hover:bg-hover transition-colors"
        >
          {hideBalances ? <EyeOff className="w-4 h-4" />:<Eye className="w-4 h-4" />}
          <span>{hideBalances ? '잔액 표시' : '잔액 숨기기'}</span>
        </button>
        <button
          onClick={onCreateAccount}
          className="flex items-center space-x-2 px-4 py-2 bg-brand text-white rounded-lg hover:bg-brand-dark transition-colors"
        >
          <Plus className="w-4 h-4" />
          <span>계좌 생성</span>
        </button>
      </div>
    </div>
  );
};
