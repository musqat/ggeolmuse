import React from 'react';
import { RefreshCw, Trash2, DollarSign, TrendingUp, ArrowUpDown, ChevronLeft, ChevronRight } from 'lucide-react';
import type { Asset } from '@services/adminApi';

interface AssetListSectionProps {
  assets: Asset[];
  loading: boolean;
  onRefresh: () => void;
  onDelete: (symbol: string) => void;
  onUpdatePrice: (symbol: string) => void;
  onUpdateMarketCap: (symbol: string) => void;
  onUpdateAllPrices: () => void;
  onUpdateAllMarketCaps: () => void;
  // Pagination props
  currentPage: number;
  pageSize: number;
  totalPages: number;
  totalElements: number;
  sortBy: string;
  sortDirection: 'asc' | 'desc';
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  onSortChange: (field: string, direction: 'asc' | 'desc') => void;
}

export default function AssetListSection({
  assets,
  loading,
  onRefresh,
  onDelete,
  onUpdatePrice,
  onUpdateMarketCap,
  onUpdateAllPrices,
  onUpdateAllMarketCaps,
  currentPage,
  pageSize,
  totalPages,
  totalElements,
  sortBy,
  sortDirection,
  onPageChange,
  onPageSizeChange,
  onSortChange,
}: AssetListSectionProps) {

  const handleSort = (field: string) => {
    if (sortBy === field) {
      // Toggle direction
      onSortChange(field, sortDirection === 'asc' ? 'desc' : 'asc');
    } else {
      // New field, default to asc
      onSortChange(field, 'asc');
    }
  };

  const formatPrice = (price?: number) => {
    if (price == null) return 'N/A';
    return `$${price.toFixed(2)}`;
  };

  const formatMarketCap = (marketCap?: number, assetType?: string) => {
    // ETF는 시가총액이 없음
    if (assetType === 'ETF') return '-';
    if (marketCap == null) return 'N/A';
    if (marketCap >= 1_000_000_000_000) {
      return `$${(marketCap / 1_000_000_000_000).toFixed(2)}T`;
    }
    if (marketCap >= 1_000_000_000) {
      return `$${(marketCap / 1_000_000_000).toFixed(2)}B`;
    }
    if (marketCap >= 1_000_000) {
      return `$${(marketCap / 1_000_000).toFixed(2)}M`;
    }
    return `$${marketCap.toLocaleString()}`;
  };

  const formatDate = (date?: string) => {
    if (!date) return 'N/A';
    return date;
  };

  const SortHeader = ({ field, label }: { field: string; label: string }) => (
    <th
      className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100 transition"
      onClick={() => handleSort(field)}
    >
      <div className="flex items-center gap-1">
        {label}
        {sortBy === field && (
          <ArrowUpDown className={`w-4 h-4 ${sortDirection === 'desc' ? 'rotate-180' : ''}`} />
        )}
      </div>
    </th>
  );

  const pageNumbers = [];
  const maxPagesToShow = 5;
  let startPage = Math.max(0, currentPage - Math.floor(maxPagesToShow / 2));
  let endPage = Math.min(totalPages - 1, startPage + maxPagesToShow - 1);

  if (endPage - startPage < maxPagesToShow - 1) {
    startPage = Math.max(0, endPage - maxPagesToShow + 1);
  }

  for (let i = startPage; i <= endPage; i++) {
    pageNumbers.push(i);
  }

  return (
    <div className="bg-white rounded-lg shadow-md p-6">
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="text-xl font-semibold">등록된 심볼 목록</h2>
          <p className="text-sm text-gray-500 mt-1">
            전체 {totalElements.toLocaleString()}개 중 {currentPage * pageSize + 1}-
            {Math.min((currentPage + 1) * pageSize, totalElements)} 표시
          </p>
        </div>
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2">
            <label className="text-sm text-gray-600">페이지당:</label>
            <select
              value={pageSize}
              onChange={(e) => onPageSizeChange(Number(e.target.value))}
              className="px-3 py-1 border rounded-lg text-sm"
              disabled={loading}
            >
              <option value={10}>10</option>
              <option value={20}>20</option>
              <option value={50}>50</option>
              <option value={100}>100</option>
            </select>
          </div>
          <button
            onClick={onRefresh}
            disabled={loading}
            className="px-4 py-2 text-indigo-600 hover:bg-indigo-50 rounded-lg flex items-center gap-2 transition"
          >
            <RefreshCw className={`w-5 h-5 ${loading ? 'animate-spin' : ''}`} />
            새로고침
          </button>
          <button
            onClick={onUpdateAllPrices}
            disabled={loading}
            className="px-4 py-2 bg-green-600 text-white hover:bg-green-700 rounded-lg flex items-center gap-2 transition disabled:opacity-50"
          >
            <DollarSign className="w-5 h-5" />
            전체 가격 업데이트
          </button>
          <button
            onClick={onUpdateAllMarketCaps}
            disabled={loading}
            className="px-4 py-2 bg-blue-600 text-white hover:bg-blue-700 rounded-lg flex items-center gap-2 transition disabled:opacity-50"
          >
            <TrendingUp className="w-5 h-5" />
            전체 시가총액 업데이트
          </button>
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full">
          <thead className="bg-gray-50">
            <tr>
              <SortHeader field="symbol" label="심볼" />
              <SortHeader field="currentPrice" label="가격" />
              <SortHeader field="marketCap" label="시가총액" />
              <SortHeader field="latestDataDate" label="최신 데이터" />
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                타입
              </th>
              <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                작업
              </th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {assets.map((asset) => (
              <tr key={asset.symbol} className="hover:bg-gray-50 transition">
                <td className="px-6 py-4 whitespace-nowrap font-semibold text-indigo-600">
                  {asset.symbol}
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  <span className={asset.currentPrice == null ? 'text-gray-400' : 'font-medium'}>
                    {formatPrice(asset.currentPrice)}
                  </span>
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  <span className={asset.marketCap == null && asset.assetType !== 'ETF' ? 'text-gray-400' : 'font-medium'}>
                    {formatMarketCap(asset.marketCap, asset.assetType)}
                  </span>
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  <span className={asset.latestDataDate == null ? 'text-gray-400' : ''}>
                    {formatDate(asset.latestDataDate)}
                  </span>
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  <span className="px-2 py-1 text-xs rounded-full bg-blue-100 text-blue-800">
                    {asset.assetType}
                  </span>
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-right">
                  <div className="flex items-center justify-end gap-2">
                    <button
                      onClick={() => onUpdatePrice(asset.symbol)}
                      disabled={loading}
                      className="px-3 py-1 text-sm text-green-600 hover:bg-green-50 rounded flex items-center gap-1 transition disabled:opacity-50"
                      title="가격 업데이트"
                    >
                      <DollarSign className="w-4 h-4" />
                      가격
                    </button>
                    {asset.assetType !== 'ETF' && (
                      <button
                        onClick={() => onUpdateMarketCap(asset.symbol)}
                        disabled={loading}
                        className="px-3 py-1 text-sm text-blue-600 hover:bg-blue-50 rounded flex items-center gap-1 transition disabled:opacity-50"
                        title="시가총액 업데이트"
                      >
                        <TrendingUp className="w-4 h-4" />
                        시총
                      </button>
                    )}
                    <button
                      onClick={() => onDelete(asset.symbol)}
                      disabled={loading}
                      className="px-3 py-1 text-sm text-red-600 hover:bg-red-50 rounded flex items-center gap-1 transition disabled:opacity-50"
                      title="삭제"
                    >
                      <Trash2 className="w-4 h-4" />
                      삭제
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {assets.length === 0 && (
          <div className="text-center py-12 text-gray-500">
            등록된 심볼이 없습니다. 검색을 통해 심볼을 추가하세요.
          </div>
        )}
      </div>

      {/* Pagination Controls */}
      {totalPages > 1 && (
        <div className="mt-6 flex items-center justify-between border-t pt-4">
          <div className="text-sm text-gray-600">
            페이지 {currentPage + 1} / {totalPages}
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => onPageChange(0)}
              disabled={currentPage === 0 || loading}
              className="px-3 py-1 text-sm border rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              처음
            </button>
            <button
              onClick={() => onPageChange(currentPage - 1)}
              disabled={currentPage === 0 || loading}
              className="px-3 py-1 border rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>

            {pageNumbers.map((pageNum) => (
              <button
                key={pageNum}
                onClick={() => onPageChange(pageNum)}
                disabled={loading}
                className={`px-3 py-1 text-sm border rounded-lg transition ${
                  currentPage === pageNum
                    ? 'bg-indigo-600 text-white border-indigo-600'
                    : 'hover:bg-gray-50'
                }`}
              >
                {pageNum + 1}
              </button>
            ))}

            <button
              onClick={() => onPageChange(currentPage + 1)}
              disabled={currentPage >= totalPages - 1 || loading}
              className="px-3 py-1 border rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
            <button
              onClick={() => onPageChange(totalPages - 1)}
              disabled={currentPage >= totalPages - 1 || loading}
              className="px-3 py-1 text-sm border rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              마지막
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
