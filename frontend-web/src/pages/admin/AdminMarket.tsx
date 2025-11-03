import React from 'react';
import { TrendingUp } from 'lucide-react';
import { useAdminMarket } from '@hooks/useAdminMarket';
import AssetSearchSection from '@components/admin/AssetSearchSection';
import AssetPreviewSection from '@components/admin/AssetPreviewSection';
import AssetListSection from '@components/admin/AssetListSection';

export default function AdminMarket() {
  const {
    searchKeyword,
    setSearchKeyword,
    searchResults,
    preview,
    assets,
    loading,
    error,
    handleSearch,
    handlePreview,
    handleAddAsset,
    handleDeleteAsset,
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
  } = useAdminMarket();

  return (
    <div className="min-h-screen bg-gray-50 py-8">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-gray-900 flex items-center gap-2">
            <TrendingUp className="w-8 h-8 text-indigo-600" />
            Market Data 관리
          </h1>
          <p className="mt-2 text-gray-600">
            심볼 검색, 추가, 삭제 및 데이터 수집 관리
          </p>
        </div>

        {/* Error Alert */}
        {error && (
          <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg">
            <p className="text-red-800">{error}</p>
          </div>
        )}

        {/* Search Section */}
        <AssetSearchSection
          searchKeyword={searchKeyword}
          searchResults={searchResults}
          loading={loading}
          onSearchKeywordChange={setSearchKeyword}
          onSearch={handleSearch}
          onPreview={handlePreview}
        />

        {/* Preview Section */}
        {preview && (
          <AssetPreviewSection
            preview={preview}
            loading={loading}
            onAdd={handleAddAsset}
          />
        )}

        {/* Assets List */}
        <AssetListSection
          assets={assets}
          loading={loading}
          onRefresh={loadAssets}
          onDelete={handleDeleteAsset}
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
        />
      </div>
    </div>
  );
}
