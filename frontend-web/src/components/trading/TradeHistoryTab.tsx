import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { tradeApi, type TransactionHistoryItem } from '../../services/api';
import { accountListQuery } from '../../hooks/useAccounts';
import { TradeHistoryList } from './history/TradeHistoryList';
import { historyRefetchInterval } from './history/tradeHistory';
import { Spinner } from '../common/Spinner';

// 거래 화면의 거래 내역 탭(거래 내역 페이지와 같은 목록)
const TradeHistoryTab: React.FC = () => {
  const { data: accounts = [] } = useQuery(accountListQuery);

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
    staleTime: 2 * 60 * 1000,
    refetchInterval: (query) => historyRefetchInterval(query.state.data),
  });

  if (loading) {
    return (
      <div className="flex justify-center items-center py-12">
        <Spinner size="md" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-center py-12">
        <p className="text-loss mb-4">거래내역을 불러오는데 실패했습니다.</p>
        <button
          onClick={() => refetch()}
          className="px-4 py-2 bg-brand text-brand-ink rounded-lg hover:bg-brand-dark"
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
    />
  );
};

export default TradeHistoryTab;
