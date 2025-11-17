import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { TrendingUp, TrendingDown, DollarSign, Package } from 'lucide-react';
import { tradeApi } from '../../services/api';

interface TradingCapacityPanelProps {
  accountId: number | null;
  symbol: string;
  tradeDate: string;
  orderType: 'buy' | 'sell';
  currentPrice: number;
  selectedDateOHLC: any | null;
}

interface CapacityData {
  maxShares?: number;
  availableBalance?: number;
  currentHoldings?: number;
  maxSellableShares?: number;
  totalValue?: number;
  currency?: string;
}

const TradingCapacityPanel: React.FC<TradingCapacityPanelProps> = ({
  accountId,
  symbol,
  tradeDate,
  orderType,
  currentPrice,
  selectedDateOHLC,
}) => {
  // React Query: 거래 가능 수량 조회
  const {
    data: capacity = null,
    isLoading: loading
  } = useQuery({
    queryKey: ['trade', 'capacity', accountId, symbol, tradeDate, orderType],
    queryFn: async () => {
      const payload = {
        accountId: String(accountId),
        symbol,
        tradeDate: tradeDate,
      };

      if (orderType === 'buy') {
        const response = await tradeApi.canBuy(payload);
        return response.data;
      } else {
        const response = await tradeApi.canSell(payload);
        return response.data;
      }
    },
    enabled: !!accountId && !!symbol && !!tradeDate && currentPrice > 0,
    staleTime: 30 * 1000, // 30초 (실시간 데이터이므로 짧게)
  });

  if (!accountId || !capacity) {
    return null;
  }

  if (loading) {
    return (
      <div className="bg-gradient-to-br from-indigo-50 to-purple-50 border border-indigo-200 rounded-lg p-4 mb-4">
        <div className="flex items-center justify-center">
          <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-600"></div>
          <span className="ml-2 text-sm text-indigo-600">거래 가능 수량 계산 중...</span>
        </div>
      </div>
    );
  }

  if (orderType === 'buy') {
    return (
      <div className="bg-gradient-to-br from-green-50 to-emerald-50 border border-green-200 rounded-lg p-4 mb-4">
        <div className="flex items-center mb-3">
          <TrendingUp className="w-5 h-5 text-green-600 mr-2" />
          <h4 className="font-semibold text-green-800">매수 가능 정보</h4>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="bg-white/60 rounded-lg p-3">
            <div className="flex items-center mb-1">
              <Package className="w-4 h-4 text-green-600 mr-1" />
              <p className="text-xs text-gray-600">최대 매수 가능</p>
            </div>
            <p className="text-lg font-bold text-green-700">
              {capacity.maxShares?.toFixed(2) || '0'} 주
            </p>
          </div>

          <div className="bg-white/60 rounded-lg p-3">
            <div className="flex items-center mb-1">
              <DollarSign className="w-4 h-4 text-green-600 mr-1" />
              <p className="text-xs text-gray-600">사용 가능 잔액</p>
            </div>
            <p className="text-lg font-bold text-green-700">
              ${capacity.availableBalance?.toFixed(2) || '0'}
            </p>
          </div>
        </div>

        <div className="mt-3 p-2 bg-green-100/50 rounded text-xs text-green-700">
          💡 현재가 ${currentPrice.toFixed(2)} 기준 최대 {capacity.maxShares?.toFixed(2) || '0'}주 매수 가능
        </div>
      </div>
    );
  } else {
    // 매도인 경우
    const hasHoldings = (capacity.currentHoldings ?? 0) > 0;

    return (
      <div className={`border rounded-lg p-4 mb-4 ${
        hasHoldings
          ? 'bg-gradient-to-br from-red-50 to-pink-50 border-red-200'
          : 'bg-gradient-to-br from-gray-50 to-slate-50 border-gray-300'
      }`}>
        <div className="flex items-center mb-3">
          <TrendingDown className={`w-5 h-5 mr-2 ${hasHoldings ? 'text-red-600' : 'text-gray-500'}`} />
          <h4 className={`font-semibold ${hasHoldings ? 'text-red-800' : 'text-gray-700'}`}>
            매도 가능 정보
          </h4>
        </div>

        {hasHoldings ? (
          <>
            <div className="grid grid-cols-2 gap-3">
              <div className="bg-white/60 rounded-lg p-3">
                <div className="flex items-center mb-1">
                  <Package className="w-4 h-4 text-red-600 mr-1" />
                  <p className="text-xs text-gray-600">보유 수량</p>
                </div>
                <p className="text-lg font-bold text-red-700">
                  {capacity.currentHoldings?.toFixed(2) || '0'} 주
                </p>
              </div>

              <div className="bg-white/60 rounded-lg p-3">
                <div className="flex items-center mb-1">
                  <TrendingDown className="w-4 h-4 text-red-600 mr-1" />
                  <p className="text-xs text-gray-600">매도 가능</p>
                </div>
                <p className="text-lg font-bold text-red-700">
                  {capacity.maxSellableShares?.toFixed(2) || '0'} 주
                </p>
              </div>
            </div>

            {capacity.totalValue !== undefined && capacity.totalValue !== null && (
              <div className="mt-3 grid grid-cols-1 gap-2">
                <div className="bg-white/60 rounded-lg p-3">
                  <p className="text-xs text-gray-600 mb-1">현재 평가액</p>
                  <p className="text-xl font-bold text-red-700">
                    ${capacity.totalValue.toFixed(2)}
                  </p>
                </div>
              </div>
            )}

            <div className="mt-3 p-2 bg-red-100/50 rounded text-xs text-red-700">
              💡 {tradeDate} 이전에 매수한 {capacity.maxSellableShares?.toFixed(2) || '0'}주만 매도 가능 (FIFO)
            </div>
          </>
        ) : (
          <div className="bg-white/60 rounded-lg p-4 text-center">
            <Package className="w-8 h-8 text-gray-400 mx-auto mb-2" />
            <p className="text-sm text-gray-600 font-medium">보유 종목 없음</p>
            <p className="text-xs text-gray-500 mt-1">
              {symbol} 종목을 보유하고 있지 않습니다
            </p>
          </div>
        )}
      </div>
    );
  }
};

export default TradingCapacityPanel;
