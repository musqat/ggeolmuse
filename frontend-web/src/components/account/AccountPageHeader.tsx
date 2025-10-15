import React from 'react';
import { Plus, Eye, EyeOff } from 'lucide-react';

/**
 * 계좌 페이지 헤더 컴포넌트의 Props
 */
export interface AccountPageHeaderProps {
  /** 잔액 숨김 여부 */
  hideBalances: boolean;
  /** 잔액 표시/숨김 토글 핸들러 */
  onToggleBalances: () => void;
  /** 계좌 생성 버튼 클릭 핸들러 */
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
        <h1 className="text-3xl font-bold text-gray-900">계좌 관리</h1>
        <p className="text-gray-600 mt-1">투자 계좌를 관리하세요</p>
      </div>
      <div className="flex items-center space-x-3 mt-4 md:mt-0">
        <button
          onClick={onToggleBalances}
          className="flex items-center space-x-2 px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors"
        >
          {hideBalances ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
          <span>{hideBalances ? '잔액 표시' : '잔액 숨기기'}</span>
        </button>
        <button
          onClick={onCreateAccount}
          className="flex items-center space-x-2 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
        >
          <Plus className="w-4 h-4" />
          <span>계좌 생성</span>
        </button>
      </div>
    </div>
  );
};
