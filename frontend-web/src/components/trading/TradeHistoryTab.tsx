import React, { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  ArrowUpCircle,
  ArrowDownCircle,
  RefreshCw,
  DollarSign,
  Filter
} from 'lucide-react';
import { tradeApi, accountsApi } from '../../services/api';

interface Transaction {
  type: 'BUY' | 'SELL' | 'DIVIDEND';
  tradeId?: string;
  accountId?: number;
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

interface Account {
  accountId: number;
  accountName: string;
  usdBalance: number;
}

type TransactionType = 'ALL' | 'BUY' | 'SELL' | 'DIVIDEND';

const TradeHistoryTab: React.FC = () => {
  const [selectedAccountId, setSelectedAccountId] = useState<number | 'ALL'>('ALL');
  const [selectedType, setSelectedType] = useState<TransactionType>('ALL');

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
    error: queryError,
    refetch
  } = useQuery({
    queryKey: ['trade', 'history'],
    queryFn: async () => {
      const response = await tradeApi.history();
      return response.data || [];
    },
    staleTime: 2 * 60 * 1000, // 2분
  });

  const error = queryError ? '거래내역을 불러오는데 실패했습니다.' : null;

  // 필터링된 거래내역
  const filteredTransactions = useMemo(() => {
    let filtered = [...transactions];

    // 계좌 필터링
    if (selectedAccountId !== 'ALL') {
      filtered = filtered.filter((tx) =>
        tx.accountId === selectedAccountId
      );
    }

    // 거래 유형 필터링
    if (selectedType !== 'ALL') {
      filtered = filtered.filter((tx) => tx.type === selectedType);
    }

    return filtered;
  }, [transactions, selectedAccountId, selectedType]);

  // Trade 단위로 그룹화 (배당 필터 시에는 원본 거래 포함)
  const groupedTransactions = useMemo((): {
    grouped: Array<{ trade: Transaction; dividends: Transaction[] }>;
    sellTrades: Transaction[];
    dividendsOnly: Transaction[];
  } => {
    if (!filteredTransactions || filteredTransactions.length === 0) {
      return { grouped: [], sellTrades: [], dividendsOnly: [] };
    }

    // 배당만 필터링한 경우
    if (selectedType === 'DIVIDEND') {
      const dividendsOnly = filteredTransactions
        .filter(tx => tx.type === 'DIVIDEND')
        .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
      return { grouped: [], sellTrades: [], dividendsOnly };
    }

    const buyTrades = filteredTransactions
      .filter(tx => tx.type === 'BUY')
      .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());

    const sellTrades = filteredTransactions
      .filter(tx => tx.type === 'SELL')
      .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());

    const grouped: Array<{ trade: Transaction; dividends: Transaction[] }> = [];

    for (const buyTrade of buyTrades) {
      const relatedDividends = transactions
        .filter(tx => tx.type === 'DIVIDEND' && tx.tradeId === buyTrade.tradeId)
        .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());

      grouped.push({ trade: buyTrade, dividends: relatedDividends });
    }

    return { grouped, sellTrades, dividendsOnly: [] };
  }, [filteredTransactions, selectedType, transactions]);

  if (loading) {
    return (
      <div className="flex justify-center items-center py-12">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-center py-12">
        <p className="text-red-600 mb-4">{error}</p>
        <button
          onClick={() => refetch()}
          className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700"
        >
          다시 시도
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Filter Section */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-100 p-4">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center space-x-2">
            <Filter className="w-5 h-5 text-gray-600" />
            <h3 className="text-lg font-semibold text-gray-900">필터</h3>
          </div>
          <button
            onClick={() => refetch()}
            className="flex items-center space-x-1 px-3 py-1.5 text-sm text-indigo-600 hover:bg-indigo-50 rounded-md transition-colors"
          >
            <RefreshCw className="w-4 h-4" />
            <span>새로고침</span>
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Account Filter */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              계좌
            </label>
            <select
              value={selectedAccountId}
              onChange={(e) => setSelectedAccountId(e.target.value === 'ALL' ? 'ALL' : Number(e.target.value))}
              className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
            >
              <option value="ALL">전체 계좌</option>
              {accounts.map(acc => (
                <option key={acc.accountId} value={acc.accountId}>
                  {acc.accountName} (${acc.usdBalance.toFixed(2)})
                </option>
              ))}
            </select>
          </div>

          {/* Type Filter */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              거래 유형
            </label>
            <div className="flex space-x-2">
              {[
                { value: 'ALL', label: '전체' },
                { value: 'BUY', label: '매수' },
                { value: 'SELL', label: '매도' },
                { value: 'DIVIDEND', label: '배당' }
              ].map(({ value, label }) => (
                <button
                  key={value}
                  onClick={() => setSelectedType(value as TransactionType)}
                  className={`flex-1 px-3 py-2 text-sm rounded-md font-medium transition-colors ${
                    selectedType === value
                      ? 'bg-indigo-600 text-white'
                      : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Summary */}
        <div className="mt-4 pt-4 border-t border-gray-200">
          <p className="text-sm text-gray-600">
            총 <span className="font-semibold text-indigo-600">{filteredTransactions.length}건</span>의 거래 내역
          </p>
        </div>
      </div>

      {/* Transaction List */}
      {filteredTransactions.length === 0 ? (
        <div className="text-center py-12 bg-white rounded-lg shadow-sm border border-gray-100">
          <p className="text-gray-500">거래 내역이 없습니다</p>
        </div>
      ) : (
        <div className="space-y-3">
          {/* Dividends Only (when DIVIDEND filter is active) */}
          {groupedTransactions.dividendsOnly.map((dividend) => (
            <div key={`${dividend.tradeId}-${dividend.date}`} className="bg-white rounded-lg shadow-sm border border-gray-100 overflow-hidden">
              <div className="p-3 bg-green-50 hover:bg-green-100 transition-colors">
                <div className="flex items-center justify-between">
                  <div className="flex items-center space-x-3">
                    <DollarSign className="w-5 h-5 text-green-600 flex-shrink-0" />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center space-x-2">
                        <span className="font-semibold text-gray-900">{dividend.symbol}</span>
                        <span className="text-xs font-medium text-green-700">배당</span>
                        <span className="text-xs text-gray-500">
                          {new Date(dividend.date).toLocaleDateString('ko-KR')}
                        </span>
                        {accounts.find(acc => acc.accountId === dividend.accountId) && (
                          <span className="text-xs px-2 py-0.5 bg-purple-100 text-purple-700 rounded">
                            {accounts.find(acc => acc.accountId === dividend.accountId)!.accountName}
                          </span>
                        )}
                      </div>
                      <p className="text-xs text-gray-600">
                        {dividend.shares?.toFixed(2)}주 × ${dividend.dividendPerShare?.toFixed(2)}/주
                      </p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="font-semibold text-green-700 text-sm">
                      +${dividend.totalAmount.toFixed(2)}
                    </p>
                    <p className="text-xs text-gray-500">세후</p>
                  </div>
                </div>
              </div>
            </div>
          ))}

          {/* Buy Trades with Dividends */}
          {groupedTransactions.grouped.map(({ trade, dividends }) => (
            <div key={trade.tradeId} className="bg-white rounded-lg shadow-sm border border-gray-100 overflow-hidden">
              {/* Buy Trade */}
              <div className="p-3 hover:bg-gray-50 transition-colors">
                <div className="flex items-center justify-between">
                  <div className="flex items-center space-x-3">
                    <ArrowUpCircle className="w-5 h-5 text-blue-500 flex-shrink-0" />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center space-x-2">
                        <span className="font-semibold text-gray-900">{trade.symbol}</span>
                        <span className="text-xs text-gray-500">
                          {new Date(trade.date).toLocaleDateString('ko-KR')}
                        </span>
                        {accounts.find(acc => acc.accountId === trade.accountId) && (
                          <span className="text-xs px-2 py-0.5 bg-purple-100 text-purple-700 rounded">
                            {accounts.find(acc => acc.accountId === trade.accountId)!.accountName}
                          </span>
                        )}
                      </div>
                      <p className="text-xs text-gray-600">
                        {trade.quantity?.toFixed(2)}주 × ${trade.price?.toFixed(2)}
                      </p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="font-semibold text-gray-900 text-sm">${trade.totalAmount.toFixed(2)}</p>
                  </div>
                </div>
              </div>

              {/* Dividends */}
              {dividends.length > 0 && (
                <div className="bg-green-50 border-t border-green-100">
                  {dividends.map((dividend) => (
                    <div
                      key={`${dividend.tradeId}-${dividend.date}`}
                      className="p-3 pl-8 hover:bg-green-100 transition-colors"
                    >
                      <div className="flex items-center justify-between">
                        <div className="flex items-center space-x-2">
                          <DollarSign className="w-4 h-4 text-green-600 flex-shrink-0" />
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center space-x-2">
                              <span className="text-xs font-medium text-gray-700">배당</span>
                              <span className="text-xs text-gray-500">
                                {new Date(dividend.date).toLocaleDateString('ko-KR')}
                              </span>
                            </div>
                            <p className="text-xs text-gray-600">
                              {dividend.shares?.toFixed(2)}주 × ${dividend.dividendPerShare?.toFixed(2)}/주
                            </p>
                          </div>
                        </div>
                        <div className="text-right">
                          <p className="font-semibold text-green-700 text-sm">
                            +${dividend.totalAmount.toFixed(2)}
                          </p>
                          <p className="text-xs text-gray-500">세후</p>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ))}

          {/* Sell Trades */}
          {groupedTransactions.sellTrades.map((trade) => (
            <div key={trade.tradeId} className="bg-white rounded-lg shadow-sm border border-gray-100 overflow-hidden">
              <div className="p-3 hover:bg-gray-50 transition-colors">
                <div className="flex items-center justify-between">
                  <div className="flex items-center space-x-3">
                    <ArrowDownCircle className="w-5 h-5 text-red-500 flex-shrink-0" />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center space-x-2">
                        <span className="font-semibold text-gray-900">{trade.symbol}</span>
                        <span className="text-xs text-gray-500">
                          {new Date(trade.date).toLocaleDateString('ko-KR')}
                        </span>
                        {accounts.find(acc => acc.accountId === trade.accountId) && (
                          <span className="text-xs px-2 py-0.5 bg-purple-100 text-purple-700 rounded">
                            {accounts.find(acc => acc.accountId === trade.accountId)!.accountName}
                          </span>
                        )}
                      </div>
                      <p className="text-xs text-gray-600">
                        {trade.quantity?.toFixed(2)}주 × ${trade.price?.toFixed(2)}
                      </p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="font-semibold text-gray-900 text-sm">${trade.totalAmount.toFixed(2)}</p>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default TradeHistoryTab;
