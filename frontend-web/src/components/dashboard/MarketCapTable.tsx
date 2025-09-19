import React from 'react';
import { TrendingUp, TrendingDown } from 'lucide-react';
import type { Stock, SupportedSymbol } from '../../types/stock';

interface MarketCapTableProps {
  stocks: Stock[];
  onStockSelect: (symbol: SupportedSymbol) => void;
}

const MarketCapTable: React.FC<MarketCapTableProps> = ({ stocks, onStockSelect }) => {
  const formatPrice = (price: number): string => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(price);
  };

  const formatChange = (change: number): string => {
    const sign = change >= 0 ? '+' : '';
    return `${sign}${change.toFixed(2)}`;
  };

  const formatChangePercent = (changePercent: number): string => {
    const sign = changePercent >= 0 ? '+' : '';
    return `${sign}${changePercent.toFixed(2)}%`;
  };

  const getChangeColor = (change: number): string => {
    return change >= 0 ? 'text-red-600' : 'text-blue-600';
  };

  const getChangeBgColor = (change: number): string => {
    return change >= 0 ? 'bg-red-50' : 'bg-blue-50';
  };

  const getRankBadgeColor = (rank: number): string => {
    if (rank === 1) return 'bg-yellow-100 text-yellow-800';
    if (rank === 2) return 'bg-gray-100 text-gray-800';
    if (rank === 3) return 'bg-orange-100 text-orange-800';
    return 'bg-green-100 text-green-800';
  };

  if (stocks.length === 0) {
    return (
        <div className="bg-white rounded-xl shadow-sm p-6">
          <div className="animate-pulse">
            <div className="h-6 bg-gray-200 rounded mb-4"></div>
            <div className="space-y-3">
              {[...Array(5)].map((_, i) => (
                  <div key={i} className="h-16 bg-gray-200 rounded"></div>
              ))}
            </div>
          </div>
        </div>
    );
  }

  return (
      <div className="bg-white rounded-xl shadow-sm overflow-hidden">
        {/* 헤더 */}
        <div className="bg-gray-50 px-6 py-4 border-b border-gray-200">
          <div className="flex justify-between items-center">
            <h3 className="text-lg font-semibold text-gray-900 flex items-center gap-2">
              📈 시가총액 순위 (TOP 5)
            </h3>
            <div className="flex gap-2">
              <button className="text-sm text-gray-600 hover:text-gray-900 px-3 py-1 rounded-md hover:bg-gray-100 transition-colors">
                전체보기
              </button>
              <button className="text-sm text-gray-600 hover:text-gray-900 px-3 py-1 rounded-md hover:bg-gray-100 transition-colors">
                관심종목
              </button>
            </div>
          </div>
        </div>

        {/* 테이블 */}
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">
                순위
              </th>
              <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">
                종목
              </th>
              <th className="px-6 py-3 text-right text-xs font-semibold text-gray-600 uppercase tracking-wider">
                현재가
              </th>
              <th className="px-6 py-3 text-right text-xs font-semibold text-gray-600 uppercase tracking-wider">
                변동률
              </th>
              <th className="px-6 py-3 text-right text-xs font-semibold text-gray-600 uppercase tracking-wider">
                시가총액
              </th>
            </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
            {stocks.map((stock, index) => (
                <tr
                    key={stock.symbol}
                    onClick={() => onStockSelect(stock.symbol as SupportedSymbol)}
                    className="hover:bg-gray-50 cursor-pointer transition-colors group"
                >
                  {/* 순위 */}
                  <td className="px-6 py-4 whitespace-nowrap">
                  <span className={`inline-flex items-center justify-center w-8 h-8 rounded-full text-sm font-bold ${getRankBadgeColor(index + 1)}`}>
                    {index + 1}
                  </span>
                  </td>

                  {/* 종목 정보 */}
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="flex flex-col">
                      <div className="text-sm font-semibold text-gray-900 group-hover:text-indigo-600 transition-colors">
                        {stock.symbol}
                      </div>
                      <div className="text-xs text-gray-500 mt-1">
                        {stock.name}
                      </div>
                    </div>
                  </td>

                  {/* 현재가 */}
                  <td className="px-6 py-4 whitespace-nowrap text-right">
                    <div className="text-sm font-semibold text-gray-900">
                      {formatPrice(stock.currentPrice)}
                    </div>
                  </td>

                  {/* 변동률 */}
                  <td className="px-6 py-4 whitespace-nowrap text-right">
                    <div className={`inline-flex items-center gap-1 px-2 py-1 rounded-md text-sm font-medium ${getChangeBgColor(stock.changePercent)} ${getChangeColor(stock.changePercent)}`}>
                      {stock.changePercent >= 0 ? (
                          <TrendingUp className="w-4 h-4" />
                      ) : (
                          <TrendingDown className="w-4 h-4" />
                      )}
                      {formatChangePercent(stock.changePercent)}
                    </div>
                    <div className={`text-xs ${getChangeColor(stock.changePercent)} mt-1`}>
                      {formatChange(stock.change)}
                    </div>
                  </td>

                  {/* 시가총액 */}
                  <td className="px-6 py-4 whitespace-nowrap text-right">
                    <div className="text-sm font-semibold text-gray-900">
                      {stock.marketCap}
                    </div>
                  </td>
                </tr>
            ))}
            </tbody>
          </table>
        </div>

        {/* 모바일용 간단 뷰 */}
        <div className="md:hidden">
          <div className="divide-y divide-gray-200">
            {stocks.map((stock, index) => (
                <div
                    key={stock.symbol}
                    onClick={() => onStockSelect(stock.symbol as SupportedSymbol)}
                    className="p-4 hover:bg-gray-50 cursor-pointer"
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                  <span className={`flex items-center justify-center w-6 h-6 rounded-full text-xs font-bold ${getRankBadgeColor(index + 1)}`}>
                    {index + 1}
                  </span>
                      <div>
                        <div className="font-semibold text-gray-900">{stock.symbol}</div>
                        <div className="text-xs text-gray-500">{stock.name}</div>
                      </div>
                    </div>
                    <div className="text-right">
                      <div className="font-semibold text-gray-900">{formatPrice(stock.currentPrice)}</div>
                      <div className={`text-xs ${getChangeColor(stock.changePercent)}`}>
                        {formatChangePercent(stock.changePercent)}
                      </div>
                    </div>
                  </div>
                </div>
            ))}
          </div>
        </div>
      </div>
  );
};

export default MarketCapTable;