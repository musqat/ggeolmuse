import React from 'react';
import type { Account } from '@/hooks/useAccounts';

interface AccountSelectorProps {
  accounts: Account[];
  selectedAccountId: number | null;
  onAccountChange: (accountId: number | null) => void;
}

const AccountSelector: React.FC<AccountSelectorProps> = ({
  accounts,
  selectedAccountId,
  onAccountChange,
}) => {
  return (
    <div className="mb-4">
      <label className="block text-sm font-medium text-gray-700 mb-2">계좌</label>
      <select
        value={selectedAccountId ?? ''}
        onChange={(e) => onAccountChange(e.target.value ? parseInt(e.target.value, 10) : null)}
        className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
      >
        {accounts.length === 0 && <option value="">계좌 없음</option>}
        {accounts.map(acc => (
          <option key={acc.accountId} value={acc.accountId}>
            {acc.accountName} - ${(acc.usdBalance || 0).toFixed(2)}
          </option>
        ))}
      </select>
    </div>
  );
};

export default AccountSelector;
