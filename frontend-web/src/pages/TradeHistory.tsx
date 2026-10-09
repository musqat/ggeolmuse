import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../hooks/useAuth';
import { LogIn } from 'lucide-react';
import { tradeApi, accountsApi, type TransactionHistoryItem } from '../services/api';
import LoginModal from '../components/auth/LoginModal';
import { TradeHistoryList } from '../components/trading/history/TradeHistoryList';

const TradeHistory: React.FC = () => {
  const { isAuthenticated, isLoading: authLoading, login } = useAuth();
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);

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
    enabled: isAuthenticated,
    staleTime: 2 * 60 * 1000, // 2분 (거래내역은 자주 변경됨)
  });

  // React Query: 계좌 목록 조회. 계좌 필터와 계좌 이름에 쓴다
  const { data: accounts = [] } = useQuery({
    queryKey: ['accounts', 'list'],
    queryFn: async () => {
      const response = await accountsApi.getAccounts();
      return response.data || [];
    },
    enabled: isAuthenticated,
    staleTime: 5 * 60 * 1000, // 5분
  });

  if (authLoading) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="flex items-center justify-center h-64">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-brand"></div>
        </div>
      </div>
    );
  }

  // 로그인 안 됨
  if (!isAuthenticated) {
    return (
      <>
        <div className="max-w-7xl mx-auto px-4 py-6">
          <div className="min-h-[60vh] flex items-center justify-center">
            <div className="text-center">
              <h1 className="text-3xl font-bold text-tx-1 mb-4">로그인이 필요한 서비스입니다</h1>
              <p className="text-lg text-tx-2 mb-6">
                거래내역을 확인하시려면 먼저 로그인해주세요
              </p>
              <button
                onClick={() => setIsLoginModalOpen(true)}
                className="flex items-center space-x-2 bg-brand text-white px-6 py-3 rounded-lg hover:bg-brand-dark transition-colors mx-auto"
              >
                <LogIn className="w-5 h-5" />
                <span>로그인하기</span>
              </button>
            </div>
          </div>
        </div>
        <LoginModal
          isOpen={isLoginModalOpen}
          onClose={() => setIsLoginModalOpen(false)}
          onSwitchToSignup={() => {
            setIsLoginModalOpen(false);
          }}
          onLogin={async (email: string, password: string) => {
            await login(email, password);
          }}
        />
      </>
    );
  }

  if (loading) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="flex items-center justify-center h-64">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-brand"></div>
        </div>
      </div>
    );
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
          refreshing={isFetching}
        />
      </div>
    </div>
  );
};

export default TradeHistory;
