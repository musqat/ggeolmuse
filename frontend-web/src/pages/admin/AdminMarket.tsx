import React, { useState } from 'react';
import { TrendingUp, Search, Plus, X } from 'lucide-react';
import { useAdminMarket } from '@hooks/useAdminMarket';
import AssetListSection from '@components/admin/AssetListSection';
import AddAssetModal from '@components/admin/AddAssetModal';

export default function AdminMarket() {
  const {
    searchKeyword,
    setSearchKeyword,
    searchResults,
    searchActive,
    clearSearch,
    preview,
    resetPreview,
    assets,
    loading,
    error,
    handleSearch,
    handlePreview,
    handleAddAsset,
    handleDeleteAsset,
    handleBulkDelete,
    handleUpdatePrice,
    handleUpdateMarketCap,
    handleUpdateAllPrices,
    handleUpdateAllMarketCaps,
    loadAssets,
    currentPage,
    pageSize,
    totalPages,
    totalElements,
    sortBy,
    sortDirection,
    handlePageChange,
    handlePageSizeChange,
    handleSortChange,
    selected,
    toggleOne,
    toggleAll,
  } = useAdminMarket();

  const [addOpen, setAddOpen] = useState(false);

  // 검색 모드면 검색 결과, 아니면 페이지네이션 목록
  const displayAssets = searchActive ? searchResults : assets;

  return (
    <div className="min-h-screen bg-surface/50 py-8">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="mb-8 flex items-start justify-between">
          <div>
            <h1 className="text-3xl font-bold text-tx-1 flex items-center gap-2">
              <TrendingUp className="w-8 h-8 text-brand" />
              Market Data 관리
            </h1>
            <p className="mt-2 text-tx-2">보유 종목 검색·관리 및 신규 종목 추가</p>
          </div>
          <button
            onClick={() => setAddOpen(true)}
            className="px-4 py-2 bg-brand text-white rounded-lg hover:bg-brand-dark flex items-center gap-2 font-semibold"
          >
            <Plus className="w-5 h-5" />
            신규 종목 추가
          </button>
        </div>

        {/* Error Alert */}
        {error && (
          <div className="mb-6 p-4 bg-red-500/10 border border-red-500/25 rounded-lg">
            <p className="text-red-600">{error}</p>
          </div>
        )}

        {/* 보유 종목 검색 (DB) */}
        <div className="bg-surface rounded-lg shadow-md p-4 mb-6">
          <div className="flex gap-2">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-tx-3" />
              <input
                type="text"
                value={searchKeyword}
                onChange={(e) => setSearchKeyword(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                placeholder="보유 종목 검색 (티커 또는 회사명, 예: AAPL / Apple)"
                className="w-full pl-9 pr-9 py-2 border border-line-strong rounded-lg focus:outline-none focus:ring-2 focus:ring-brand"
              />
              {searchActive && (
                <button
                  onClick={clearSearch}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-tx-3 hover:text-tx-1"
                  title="검색 해제"
                >
                  <X className="w-4 h-4" />
                </button>
              )}
            </div>
            <button
              onClick={handleSearch}
              disabled={loading}
              className="px-5 py-2 bg-brand text-white rounded-lg hover:bg-brand-dark disabled:opacity-50 flex items-center gap-1.5"
            >
              <Search className="w-4 h-4" />
              검색
            </button>
          </div>
          {searchActive && (
            <p className="mt-2 text-sm text-tx-2">
              "{searchKeyword}" 검색 결과 {searchResults.length}개
              {searchResults.length === 0 && ' — 보유 종목에 없습니다. 신규 종목 추가를 이용하세요.'}
            </p>
          )}
        </div>

        {/* 종목 목록 (검색 결과 or 전체) */}
        <AssetListSection
          assets={displayAssets}
          loading={loading}
          searchMode={searchActive}
          onRefresh={searchActive ? handleSearch : loadAssets}
          onDelete={handleDeleteAsset}
          onBulkDelete={handleBulkDelete}
          onUpdatePrice={handleUpdatePrice}
          onUpdateMarketCap={handleUpdateMarketCap}
          onUpdateAllPrices={handleUpdateAllPrices}
          onUpdateAllMarketCaps={handleUpdateAllMarketCaps}
          currentPage={currentPage}
          pageSize={pageSize}
          totalPages={totalPages}
          totalElements={totalElements}
          sortBy={sortBy}
          sortDirection={sortDirection}
          onPageChange={handlePageChange}
          onPageSizeChange={handlePageSizeChange}
          onSortChange={handleSortChange}
          selected={selected}
          onToggleOne={toggleOne}
          onToggleAll={toggleAll}
        />
      </div>

      {/* 신규 추가 모달 */}
      <AddAssetModal
        isOpen={addOpen}
        onClose={() => setAddOpen(false)}
        preview={preview}
        loading={loading}
        onPreview={handlePreview}
        onAdd={async () => {
          await handleAddAsset();
          setAddOpen(false);
        }}
        onResetPreview={resetPreview}
      />
    </div>
  );
}
