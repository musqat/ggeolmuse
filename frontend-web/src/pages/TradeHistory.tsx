import React, { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../contexts/AuthContext';
import {
  ArrowUpCircle,
  ArrowDownCircle,
  Calendar,
  RefreshCw,
  LogIn,
  Lock,
  DollarSign,
  Filter
} from 'lucide-react';
import { tradeApi } from '../services/api';
import LoginModal from '../components/auth/LoginModal';

interface Transaction {
  type: 'BUY' | 'SELL' | 'DIVIDEND';
  tradeId?: string; // Trade ID (매수/매도는 필수, 배당은 연결된 Trade ID)
  symbol: string;
  quantity?: number;
  price?: number;
  totalAmount: number;
  fee?: number;
  grossAmount?: number;
  taxAmount?: number;
  dividendPerShare?: number;
  shares?: number;
  date: string;
  executedAt: string;
}

type FilterType = 'ALL' | 'BUY' | 'SELL' | 'DIVIDEND';

const TradeHistory: React.FC = () => {
  const { isAuthenticated, isLoading: authLoading, login } = useAuth();
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);
  const [filter, setFilter] = useState<FilterType>('ALL');

  // React Query: 거래내역 조회
  const {
    data: transactions = [],
    isLoading: loading,
    error,
    refetch
  } = useQuery({
    queryKey: ['trade', 'history'],
    queryFn: async () => {
      const response = await tradeApi.history();
      return response.data || [];
    },
    enabled: isAuthenticated,
    staleTime: 2 * 60 * 1000, // 2분 (거래내역은 자주 변경됨)
  });

  const handleRefresh = () => {
    refetch();
  };

  // Trade 단위로 그룹화 (매수 → 그 매수의 배당들)
  const groupedTransactions = useMemo((): { grouped: Array<{ trade: Transaction; dividends: Transaction[] }>; sellTrades: Transaction[] } => {
    if (!transactions || transactions.length === 0) {
      return { grouped: [], sellTrades: [] };
    }

    // 1. BUY trades 추출 및 시간순 정렬 (최신순)
    const buyTrades = transactions
      .filter(tx => tx.type === 'BUY')
      .sort((a, b) => new Date(b.executedAt).getTime() - new Date(a.executedAt).getTime());

    // 2. SELL trades (시간순)
    const sellTrades = transactions
      .filter(tx => tx.type === 'SELL')
      .sort((a, b) => new Date(b.executedAt).getTime() - new Date(a.executedAt).getTime());

    // 3. 각 BUY trade에 연결된 배당들 찾기
    const grouped: Array<{ trade: Transaction; dividends: Transaction[] }> = [];

    for (const buyTrade of buyTrades) {
      const relatedDividends = transactions
        .filter(tx => tx.type === 'DIVIDEND' && tx.tradeId === buyTrade.tradeId)
        .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());

      grouped.push({ trade: buyTrade, dividends: relatedDividends });
    }

    return { grouped, sellTrades };
  }, [transactions]);

  // 필터링된 거래 내역
  const filteredTransactions = useMemo(() => {
    if (filter === 'ALL') {
      return groupedTransactions;
    }

    if (filter === 'BUY') {
      return {
        grouped: groupedTransactions.grouped,
        sellTrades: []
      };
    }

    if (filter === 'SELL') {
      return {
        grouped: [],
        sellTrades: groupedTransactions.sellTrades
      };
    }

    if (filter === 'DIVIDEND') {
      // 배당만 표시: 각 그룹의 배당들만 남김
      return {
        grouped: groupedTransactions.grouped
          .map(g => ({ trade: g.trade, dividends: g.dividends }))
          .filter(g => g.dividends.length > 0),
        sellTrades: []
      };
    }

    return groupedTransactions;
  }, [filter, groupedTransactions]);

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
              <Lock className="w-16 h-16 text-brand mx-auto mb-4" />
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
      <div className="space-y-6">
        {/* 헤더 */}
        <div className="flex flex-col md:flex-row md:items-center md:justify-between">
          <div>
            <h1 className="text-3xl font-bold text-tx-1">거래내역</h1>
            <p className="text-tx-2 mt-1">매수, 매도, 배당 수령 내역</p>
          </div>
          <div className="flex items-center space-x-3 mt-4 md:mt-0">
            {/* 필터 버튼 */}
            <div className="flex items-center space-x-2 bg-elevated rounded-lg p-1">
              <button
                onClick={() => setFilter('ALL')}
                className={`px-3 py-1.5 rounded text-sm font-medium transition-colors ${
                  filter === 'ALL'
                    ? 'bg-surface text-tx-1 shadow-sm'
                    : 'text-tx-2 hover:text-tx-1'
                }`}
              >
                전체
              </button>
              <button
                onClick={() => setFilter('BUY')}
                className={`px-3 py-1.5 rounded text-sm font-medium transition-colors ${
                  filter === 'BUY'
                    ? 'bg-surface text-tx-1 shadow-sm'
                    : 'text-tx-2 hover:text-tx-1'
                }`}
              >
                매수
              </button>
              <button
                onClick={() => setFilter('SELL')}
                className={`px-3 py-1.5 rounded text-sm font-medium transition-colors ${
                  filter === 'SELL'
                    ? 'bg-surface text-tx-1 shadow-sm'
                    : 'text-tx-2 hover:text-tx-1'
                }`}
              >
                매도
              </button>
              <button
                onClick={() => setFilter('DIVIDEND')}
                className={`px-3 py-1.5 rounded text-sm font-medium transition-colors ${
                  filter === 'DIVIDEND'
                    ? 'bg-surface text-tx-1 shadow-sm'
                    : 'text-tx-2 hover:text-tx-1'
                }`}
              >
                배당
              </button>
            </div>

            <button
              onClick={handleRefresh}
              disabled={loading}
              className="flex items-center space-x-2 px-4 py-2 bg-elevated text-tx-1 rounded-lg hover:bg-hover transition-colors disabled:opacity-50"
            >
              <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin':''}`} />
              <span>새로고침</span>
            </button>
          </div>
        </div>

        {error && (
          <div className="bg-red-500/10 border border-red-500/25 text-red-600 px-4 py-3 rounded-lg">
            거래내역을 불러오는데 실패했습니다.
          </div>
        )}

        {/* 거래내역 리스트 */}
        <div className="space-y-4">
          {transactions.length === 0 ? (
            <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-12 text-center">
              <Calendar className="w-16 h-16 text-tx-3 mx-auto mb-4" />
              <p className="text-tx-2 text-lg">거래 내역이 없습니다</p>
            </div>
          ) : (
            <>
              {/* 매수 그룹들 (각 매수 + 그 매수의 배당들) */}
              {filteredTransactions.grouped.map(({ trade, dividends }) => (
                <div key={trade.tradeId} className="bg-surface rounded-xl shadow-sm border border-line/50 overflow-hidden">
                  {/* 매수 거래 */}
                  <div className="p-4 hover:bg-surface/50 transition-colors">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center space-x-4">
                        <div className="flex-shrink-0">
                          <div className="flex items-center space-x-2">
                            <ArrowUpCircle className="w-5 h-5 text-blue-500" />
                            <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                              매수
                            </span>
                          </div>
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center space-x-3">
                            <p className="text-lg font-semibold text-tx-1">{trade.symbol}</p>
                            <p className="text-sm text-tx-2">
                              {new Date(trade.executedAt).toLocaleString('ko-KR')}
                            </p>
                          </div>
                          <p className="text-sm text-tx-2 mt-1">
                            {trade.quantity?.toFixed(2)}주 × ${trade.price?.toFixed(2)}
                            {trade.fee && trade.fee > 0 && (
                              <span className="text-tx-3 ml-2">(수수료 ${trade.fee.toFixed(2)})</span>
                            )}
                          </p>
                        </div>
                      </div>
                      <div className="text-right">
                        <p className="text-lg font-semibold text-tx-1">
                          ${trade.totalAmount.toFixed(2)}
                        </p>
                      </div>
                    </div>
                  </div>

                  {/* 이 매수로부터 발생한 배당들 (들여쓰기) */}
                  {dividends.length > 0 && (filter === 'ALL' || filter === 'DIVIDEND' || filter === 'BUY') && (
                    <div className="bg-green-500/10 border-t border-green-500/20">
                      {dividends.map((dividend, idx) => (
                        <div
                          key={`${dividend.tradeId}-${dividend.date}`}
                          className="p-4 pl-12 hover:bg-green-500/15 transition-colors"
                        >
                          <div className="flex items-center justify-between">
                            <div className="flex items-center space-x-4">
                              <div className="flex-shrink-0">
                                <div className="flex items-center space-x-2">
                                  <DollarSign className="w-4 h-4 text-green-600" />
                                  <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-green-200 text-green-600">
                                    배당
                                  </span>
                                </div>
                              </div>
                              <div className="flex-1 min-w-0">
                                <div className="flex items-center space-x-3">
                                  <p className="text-sm font-medium text-tx-1">{dividend.symbol}</p>
                                  <p className="text-xs text-tx-2">
                                    {new Date(dividend.date).toLocaleDateString('ko-KR')}
                                  </p>
                                </div>
                                <p className="text-xs text-tx-2 mt-1">
                                  {dividend.shares?.toFixed(2)}주 보유 × ${dividend.dividendPerShare?.toFixed(2)}/주
                                  <span className="text-tx-3 ml-2">(원천징수 15.4% 제외)</span>
                                </p>
                              </div>
                            </div>
                            <div className="text-right">
                              <p className="text-sm font-semibold text-green-600">
                                +${dividend.totalAmount.toFixed(2)}
                              </p>
                              <p className="text-xs text-tx-2">
                                세전 ${dividend.grossAmount?.toFixed(2)}
                              </p>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              ))}

              {/* 매도 거래들 (별도 표시) */}
              {filteredTransactions.sellTrades.map((trade) => (
                <div key={trade.tradeId} className="bg-surface rounded-xl shadow-sm border border-line/50 overflow-hidden">
                  <div className="p-4 hover:bg-surface/50 transition-colors">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center space-x-4">
                        <div className="flex-shrink-0">
                          <div className="flex items-center space-x-2">
                            <ArrowDownCircle className="w-5 h-5 text-red-500" />
                            <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-red-500/15 text-red-600">
                              매도
                            </span>
                          </div>
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center space-x-3">
                            <p className="text-lg font-semibold text-tx-1">{trade.symbol}</p>
                            <p className="text-sm text-tx-2">
                              {new Date(trade.executedAt).toLocaleString('ko-KR')}
                            </p>
                          </div>
                          <p className="text-sm text-tx-2 mt-1">
                            {trade.quantity?.toFixed(2)}주 × ${trade.price?.toFixed(2)}
                            {trade.fee && trade.fee > 0 && (
                              <span className="text-tx-3 ml-2">(수수료 ${trade.fee.toFixed(2)})</span>
                            )}
                          </p>
                        </div>
                      </div>
                      <div className="text-right">
                        <p className="text-lg font-semibold text-tx-1">
                          ${trade.totalAmount.toFixed(2)}
                        </p>
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default TradeHistory;
