import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { tradeApi, accountsApi, type TransactionHistoryItem } from '../../services/api';
import { TradeHistoryList } from './history/TradeHistoryList';

// 거래 화면의 거래내역 탭. 목록은 거래내역 페이지와 같은 것을 쓴다
const TradeHistoryTab: React.FC = () => {
  // React Query: 계좌 목록 조회
  const { data: accounts = [] } = useQuery({
    queryKey: ['accounts', 'list'],
    queryFn: async () => {
      const response = await accountsApi.getAccounts();
      return response.data || [];
    },
    staleTime: 5 * 60 * 1000, // 5분
  });

  // React Query: 거래내역 조회
  const {
    data: transactions = [],
    isLoading: loading,
    isFetching,
    error,
    refetch
  } = useQuery({
    queryKey: ['trade', 'history'],
    queryFn: async (): Promise<TransactionHistoryItem[]> => {
      const response = await tradeApi.history();
      return response.data || [];
    },
    staleTime: 2 * 60 * 1000, // 2분
  });

  if (loading) {
    return (
      <div className="flex justify-center items-center py-12">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-brand"></div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-center py-12">
        <p className="text-loss mb-4">거래내역을 불러오는데 실패했습니다.</p>
        <button
          onClick={() => refetch()}
          className="px-4 py-2 bg-brand text-white rounded-lg hover:bg-brand-dark"
        >
          다시 시도
        </button>
      </div>
    );
  }

  return (
    <TradeHistoryList
      transactions={transactions}
      accounts={accounts}
      onRefresh={() => refetch()}
      refreshing={isFetching}
    />
  );
};

export default TradeHistoryTab;
