import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { accountsApi } from '../services/api';

export interface Account {
  accountId: number;
  accountName: string;
  usdBalance: number;
}

interface UseAccountsReturn {
  accounts: Account[];
  selectedAccountId: number | null;
  setSelectedAccountId: (id: number | null) => void;
  loading: boolean;
  error: string | null;
}

const EMPTY_ACCOUNTS: Account[] = [];

// 로그인했을 때 계좌 목록을 받고 첫 계좌를 고름
export function useAccounts(isAuthenticated: boolean): UseAccountsReturn {
  const [selectedAccountId, setSelectedAccountId] = useState<number | null>(null);

  const { data, isLoading, error } = useQuery({
    queryKey: ['accounts', 'list'],
    enabled: isAuthenticated,
    queryFn: async () => {
      const response = await accountsApi.getAccounts();
      return Array.isArray(response.data) ? (response.data as Account[]) : EMPTY_ACCOUNTS;
    },
  });
  const accounts = isAuthenticated ? (data ?? EMPTY_ACCOUNTS) : EMPTY_ACCOUNTS;

  // 첫 계좌를 자동 선택. 로그아웃하면 선택을 비움
  useEffect(() => {
    if (!isAuthenticated) {
      setSelectedAccountId(null);
      return;
    }
    if (accounts.length > 0) {
      setSelectedAccountId((prev) => prev ?? accounts[0].accountId);
    }
  }, [isAuthenticated, accounts]);

  return {
    accounts,
    selectedAccountId,
    setSelectedAccountId,
    loading: isLoading,
    error: error ? '계좌 목록을 불러오는데 실패했습니다.' : null,
  };
}
