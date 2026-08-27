import React, { useState } from 'react';
import { RefreshCw, Trash2, DollarSign, TrendingUp, ArrowUpDown, ChevronLeft, ChevronRight, Pencil, Check, X, PlusCircle } from 'lucide-react';
import type { Asset } from '@services/adminApi';

interface AssetListSectionProps {
  assets: Asset[];
  loading: boolean;
  onRefresh: () => void;
  onDelete: (symbol: string) => void;
  onBulkDelete: () => void;
  onUpdatePrice: (symbol: string) => void;
  onUpdateMarketCap: (symbol: string) => void;
  onUpdateName: (symbol: string, name: string) => Promise<boolean> | void;
  onUpdateAllPrices: () => void;
  onUpdateAllMarketCaps: () => void;
  onCollectNewSymbols: () => void;
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
  // Bulk selection
  selected: Set<string>;
  onToggleOne: (symbol: string) => void;
  onToggleAll: (symbols: string[]) => void;
  // 검색 모드 (검색 결과 표시 — 페이지네이션 숨김)
  searchMode?: boolean;
}

export default function AssetListSection({
  assets,
  loading,
  onRefresh,
  onDelete,
  onBulkDelete,
  onUpdatePrice,
  onUpdateMarketCap,
  onUpdateName,
  onUpdateAllPrices,
  onUpdateAllMarketCaps,
  onCollectNewSymbols,
  currentPage,
  pageSize,
  totalPages,
  totalElements,
  sortBy,
  sortDirection,
  onPageChange,
  onPageSizeChange,
  onSortChange,
  selected,
  onToggleOne,
  onToggleAll,
  searchMode = false,
}: AssetListSectionProps) {
  const currentSymbols = assets.map(a => a.symbol);
  const allSelected = currentSymbols.length > 0 && currentSymbols.every(s => selected.has(s));

  // 이름 인라인 편집 상태
  const [editingSymbol, setEditingSymbol] = useState<string | null>(null);
  const [editName, setEditName] = useState('');

  const startEdit = (asset: Asset) => {
    setEditingSymbol(asset.symbol);
    setEditName(asset.name ?? '');
  };

  const cancelEdit = () => {
    setEditingSymbol(null);
    setEditName('');
  };

  const saveEdit = async (symbol: string) => {
    const ok = await onUpdateName(symbol, editName);
    if (ok !== false) cancelEdit();
  };

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
      className="px-6 py-3 text-left text-xs font-medium text-tx-2 uppercase tracking-wider cursor-pointer hover:bg-elevated transition"
      onClick={() => handleSort(field)}
    >
      <div className="flex items-center gap-1">
        {label}
        {sortBy === field && (
          <ArrowUpDown className={`w-4 h-4 ${sortDirection === 'desc'?'rotate-180':''}`} />
        )}
      </div>
    </th>
  );

  const pageNumbers = [];
  const maxPagesToShow = 5;
  let startPage = Math.max(0, currentPage - Math.floor(maxPagesToShow / 2));
  const endPage = Math.min(totalPages - 1, startPage + maxPagesToShow - 1);

  if (endPage - startPage < maxPagesToShow - 1) {
    startPage = Math.max(0, endPage - maxPagesToShow + 1);
  }

  for (let i = startPage; i <= endPage; i++) {
    pageNumbers.push(i);
  }

  return (
    <div className="bg-surface rounded-lg shadow-md p-6">
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="text-xl font-semibold">{searchMode ? '검색 결과' : '등록된 심볼 목록'}</h2>
          <p className="text-sm text-tx-2 mt-1">
            {searchMode
              ? `${assets.length}개`
              : `전체 ${totalElements.toLocaleString()}개 중 ${currentPage * pageSize + 1}-${Math.min((currentPage + 1) * pageSize, totalElements)} 표시`}
          </p>
        </div>
        <div className="flex items-center gap-4">
          {!searchMode && (
            <div className="flex items-center gap-2">
              <label className="text-sm text-tx-2">페이지당:</label>
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
          )}
          {selected.size > 0 && (
            <button
              onClick={onBulkDelete}
              disabled={loading}
              className="px-4 py-2 bg-red-600 text-white hover:bg-red-700 rounded-lg flex items-center gap-2 transition disabled:opacity-50"
            >
              <Trash2 className="w-5 h-5" />
              선택 삭제 ({selected.size})
            </button>
          )}
          <button
            onClick={() => onRefresh()}
            disabled={loading}
            className="px-4 py-2 text-brand hover:bg-brand-bg rounded-lg flex items-center gap-2 transition"
          >
            <RefreshCw className={`w-5 h-5 ${loading ? 'animate-spin':''}`} />
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
          <button
            onClick={onCollectNewSymbols}
            disabled={loading}
            title="목록을 다시 받아 DB 에 없는 종목만 추가합니다. 평일 08:00 스케줄과 같은 일입니다."
            className="px-4 py-2 bg-purple-600 text-white hover:bg-purple-700 rounded-lg flex items-center gap-2 transition disabled:opacity-50"
          >
            <PlusCircle className="w-5 h-5" />
            신규 종목 수집
          </button>
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full">
          <thead className="bg-surface/50">
            <tr>
              <th className="px-4 py-3">
                <input
                  type="checkbox"
                  checked={allSelected}
                  onChange={() => onToggleAll(currentSymbols)}
                  className="w-4 h-4 cursor-pointer"
                />
              </th>
              <SortHeader field="symbol" label="심볼" />
              <th className="px-6 py-3 text-left text-xs font-medium text-tx-2 uppercase tracking-wider">
                이름
              </th>
              <SortHeader field="currentPrice" label="가격" />
              <SortHeader field="marketCap" label="시가총액" />
              <SortHeader field="latestDataDate" label="최신 데이터" />
              <th className="px-6 py-3 text-left text-xs font-medium text-tx-2 uppercase tracking-wider">
                타입
              </th>
              <th className="px-6 py-3 text-right text-xs font-medium text-tx-2 uppercase tracking-wider">
                작업
              </th>
            </tr>
          </thead>
          <tbody className="bg-surface divide-y divide-line">
            {assets.map((asset) => (
              <tr key={asset.symbol} className={`hover:bg-surface/50 transition ${selected.has(asset.symbol) ? 'bg-brand-bg' : ''}`}>
                <td className="px-4 py-4">
                  <input
                    type="checkbox"
                    checked={selected.has(asset.symbol)}
                    onChange={() => onToggleOne(asset.symbol)}
                    className="w-4 h-4 cursor-pointer"
                  />
                </td>
                <td className="px-6 py-4 whitespace-nowrap font-semibold text-brand">
                  {asset.symbol}
                </td>
                <td className="px-6 py-4">
                  {editingSymbol === asset.symbol ? (
                    <div className="flex items-center gap-1">
                      <input
                        type="text"
                        value={editName}
                        onChange={(e) => setEditName(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') saveEdit(asset.symbol);
                          if (e.key === 'Escape') cancelEdit();
                        }}
                        autoFocus
                        className="px-2 py-1 border rounded text-sm w-56"
                      />
                      <button
                        onClick={() => saveEdit(asset.symbol)}
                        disabled={loading}
                        className="p-1 text-green-600 hover:bg-green-500/10 rounded disabled:opacity-50"
                        title="저장"
                      >
                        <Check className="w-4 h-4" />
                      </button>
                      <button
                        onClick={cancelEdit}
                        className="p-1 text-tx-2 hover:bg-elevated rounded"
                        title="취소"
                      >
                        <X className="w-4 h-4" />
                      </button>
                    </div>
                  ) : (
                    <div className="flex items-center gap-1 group">
                      <span>{asset.name}</span>
                      <button
                        onClick={() => startEdit(asset)}
                        disabled={loading}
                        className="p-1 text-tx-3 hover:text-brand hover:bg-brand-bg rounded opacity-0 group-hover:opacity-100 transition disabled:opacity-50"
                        title="이름 수정"
                      >
                        <Pencil className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  )}
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  <span className={asset.currentPrice == null ? 'text-tx-3' : 'font-medium'}>
                    {formatPrice(asset.currentPrice)}
                  </span>
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  <span className={asset.marketCap == null && asset.assetType !== 'ETF' ? 'text-tx-3' : 'font-medium'}>
                    {formatMarketCap(asset.marketCap, asset.assetType)}
                  </span>
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  <span className={asset.latestDataDate == null ? 'text-tx-3' : ''}>
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
                      className="px-3 py-1 text-sm text-green-600 hover:bg-green-500/10 rounded flex items-center gap-1 transition disabled:opacity-50"
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
                      className="px-3 py-1 text-sm text-red-600 hover:bg-red-500/10 rounded flex items-center gap-1 transition disabled:opacity-50"
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
          <div className="text-center py-12 text-tx-2">
            {searchMode ? '검색 결과가 없습니다.' : '등록된 심볼이 없습니다. 신규 종목 추가를 이용하세요.'}
          </div>
        )}
      </div>

      {/* Pagination Controls (검색 모드에선 숨김) */}
      {!searchMode && totalPages > 1 && (
        <div className="mt-6 flex items-center justify-between border-t pt-4">
          <div className="text-sm text-tx-2">
            페이지 {currentPage + 1} / {totalPages}
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => onPageChange(0)}
              disabled={currentPage === 0 || loading}
              className="px-3 py-1 text-sm border rounded-lg hover:bg-surface/50 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              처음
            </button>
            <button
              onClick={() => onPageChange(currentPage - 1)}
              disabled={currentPage === 0 || loading}
              className="px-3 py-1 border rounded-lg hover:bg-surface/50 disabled:opacity-50 disabled:cursor-not-allowed"
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
                    ? 'bg-brand text-white border-brand'
                    : 'hover:bg-surface/50'
                }`}
              >
                {pageNum + 1}
              </button>
            ))}

            <button
              onClick={() => onPageChange(currentPage + 1)}
              disabled={currentPage >= totalPages - 1 || loading}
              className="px-3 py-1 border rounded-lg hover:bg-surface/50 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
            <button
              onClick={() => onPageChange(totalPages - 1)}
              disabled={currentPage >= totalPages - 1 || loading}
              className="px-3 py-1 text-sm border rounded-lg hover:bg-surface/50 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              마지막
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
