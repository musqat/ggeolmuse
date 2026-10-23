import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../hooks/useAuth';
import { LoginRequired } from '../components/common/LoginRequired';
import { PageSpinner } from '../components/common/Spinner';
import { tradeApi, type TransactionHistoryItem } from '../services/api';
import { accountListQuery } from '../hooks/useAccounts';
import { TradeHistoryList } from '../components/trading/history/TradeHistoryList';
import { historyRefetchInterval } from '../components/trading/history/tradeHistory';

const TradeHistory: React.FC = () => {
  const { isAuthenticated, isLoading: authLoading } = useAuth();

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
    staleTime: 2 * 60 * 1000,
    refetchInterval: (query) => historyRefetchInterval(query.state.data),
  });

  // 계좌 목록(계좌 필터 · 계좌 이름)
  const { data: accounts = [] } = useQuery({ ...accountListQuery, enabled: isAuthenticated });

  if (authLoading) {
    return <PageSpinner />;
  }

  // 로그인 안 됨
  if (!isAuthenticated) {
    return <LoginRequired description="거래내역을 확인하시려면 먼저 로그인해주세요" />;
  }

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

        {error && (
          <div className="bg-loss/10 border border-loss/25 text-loss px-4 py-3 rounded-lg">
            거래내역을 불러오는데 실패했습니다.
          </div>
        )}

        <TradeHistoryList
          transactions={transactions}
          accounts={accounts}
          onRefresh={() => refetch()}
        />
      </div>
    </div>
  );
};

export default TradeHistory;
