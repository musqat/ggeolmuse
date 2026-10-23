import { useEffect, useState } from 'react';
import { queryOptions, useQuery } from '@tanstack/react-query';
import { accountsApi, type AccountSummary } from '../services/api';

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

const EMPTY_ACCOUNTS: AccountSummary[] = [];

// 계좌 목록 조회(응답이 배열이 아니면 빈 목록)
export const accountListQuery = queryOptions({
  queryKey: ['accounts', 'list'],
  queryFn: async (): Promise<AccountSummary[]> => {
    const response = await accountsApi.getAccounts();
    return Array.isArray(response.data) ? response.data : EMPTY_ACCOUNTS;
  },
});

// 로그인했을 때 계좌 목록을 받고 첫 계좌를 고름
export function useAccounts(isAuthenticated: boolean): UseAccountsReturn {
  const [selectedAccountId, setSelectedAccountId] = useState<number | null>(null);

  const { data, isLoading, error } = useQuery({ ...accountListQuery, enabled: isAuthenticated });
  const accounts: Account[] = isAuthenticated ? (data ?? EMPTY_ACCOUNTS) : EMPTY_ACCOUNTS;

  // 첫 계좌 자동 선택(로그아웃하면 비움)
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
