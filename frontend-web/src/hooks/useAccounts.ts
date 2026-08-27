import { useState, useEffect } from 'react';
import axios from 'axios';

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

export function useAccounts(isAuthenticated: boolean): UseAccountsReturn {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [selectedAccountId, setSelectedAccountId] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isAuthenticated) {
      setAccounts([]);
      setSelectedAccountId(null);
      return;
    }

    const loadAccounts = async () => {
      setLoading(true);
      setError(null);

      try {
        const token = localStorage.getItem('accessToken');
        const response = await axios.get('/api/accounts', {
          headers: { Authorization: `Bearer ${token}` }
        });

        if (response.data && Array.isArray(response.data)) {
          setAccounts(response.data);

          // 첫 번째 계좌를 자동 선택.
          // 함수형 갱신을 쓰면 현재 선택값을 effect 밖에서 읽지 않아도 된다.
          // selectedAccountId 를 의존성에 넣으면 계좌를 바꿀 때마다 목록을 다시 부른다.
          if (response.data.length > 0) {
            setSelectedAccountId((prev) => prev ?? response.data[0].accountId);
          }
        }
      } catch (err) {
        console.error('계좌 조회 실패:', err);
        setError('계좌 목록을 불러오는데 실패했습니다.');
      } finally {
        setLoading(false);
      }
    };

    loadAccounts();
  }, [isAuthenticated]);

  return {
    accounts,
    selectedAccountId,
    setSelectedAccountId,
    loading,
    error,
  };
}
