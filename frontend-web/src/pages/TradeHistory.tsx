import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../hooks/useAuth';
import { PageSpinner } from '../components/common/Spinner';
import { tradeApi, type TransactionHistoryItem } from '../services/api';
import { accountListQuery } from '../hooks/useAccounts';
import { TradeHistoryList } from '../components/trading/history/TradeHistoryList';
import { historyRefetchInterval } from '../components/trading/history/tradeHistory';
import { LoadError } from '../components/common/LoadError';
import { staleTimes } from '@services/queryKeys';

const TradeHistory: React.FC = () => {
  const { isAuthenticated } = useAuth();

  const {
    data: transactions = [],
    isLoading: loading,
    error,
    refetch
  } = useQuery({
    queryKey: ['trade', 'history'],
    queryFn: async (): Promise<TransactionHistoryItem[]> => {
      const response = await tradeApi.history();
      return response.data || [];
    },
    enabled: isAuthenticated,
    staleTime: staleTimes.recent,
    refetchInterval: (query) => historyRefetchInterval(query.state.data),
  });

  // 계좌 목록(계좌 필터 · 계좌 이름)
  const { data: accounts = [] } = useQuery({ ...accountListQuery, enabled: isAuthenticated });

  if (loading) {
    return <PageSpinner />;
  }

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      <div className="space-y-4">
        {/* 헤더 */}
        <div>
          <h1 className="text-3xl font-bold text-tx-1">거래내역</h1>
          <p className="text-tx-2 mt-1">매수 · 매도 · 배당을 거래일 최신순으로 보입니다</p>
        </div>

        {error && <LoadError message="거래내역을 불러오지 못했습니다" onRetry={() => void refetch()} />}

        {/* 못 받았는데 받아 둔 내역도 없으면 빈 목록을 보이지 않음 */}
        {!(error && transactions.length === 0) && (
          <TradeHistoryList
            transactions={transactions}
            accounts={accounts}
            onRefresh={() => refetch()}
          />
        )}
      </div>
    </div>
  );
};

export default TradeHistory;
