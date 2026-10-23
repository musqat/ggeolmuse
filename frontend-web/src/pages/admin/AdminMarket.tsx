import { useState } from 'react';
import { Search, Plus, X } from 'lucide-react';
import { useAdminMarket } from '@hooks/useAdminMarket';
import AssetListSection from '@components/admin/AssetListSection';
import AddAssetModal from '@components/admin/AddAssetModal';
import CandleRefreshSection from '@components/admin/CandleRefreshSection';

export default function AdminMarket() {
  const market = useAdminMarket();
  const [addOpen, setAddOpen] = useState(false);

  // 검색 모드면 검색 결과, 아니면 페이지네이션 목록
  const displayAssets = market.searchActive ? market.searchResults : market.assets;

  return (
    <div className="min-h-screen bg-surface/50 py-8">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="mb-8 flex items-start justify-between">
          <div>
            <h1 className="text-3xl font-bold text-tx-1">Market Data 관리</h1>
            <p className="mt-2 text-tx-2">보유 종목 검색·관리 및 신규 종목 추가</p>
          </div>
          <button
            onClick={() => setAddOpen(true)}
            className="px-4 py-2 bg-brand text-brand-ink rounded-lg hover:bg-brand-dark flex items-center gap-2 font-semibold"
          >
            <Plus className="w-5 h-5" />
            신규 종목 추가
          </button>
        </div>

        {market.error && (
          <div className="mb-6 p-4 bg-danger/10 border border-danger/25 rounded-lg">
            <p className="text-danger">{market.error}</p>
          </div>
        )}

        {/* 분할 미반영 종목 정비 */}
        <CandleRefreshSection />

        {/* 보유 종목 검색 (DB) */}
        <div className="bg-surface rounded-lg shadow-md p-4 mb-6">
          <div className="flex gap-2">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-tx-3" />
              <input
                type="text"
                value={market.searchKeyword}
                onChange={(e) => market.setSearchKeyword(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && market.handleSearch()}
                placeholder="보유 종목 검색 (티커 또는 회사명, 예: AAPL / Apple)"
                className="w-full pl-9 pr-9 py-2 border border-line-strong rounded-lg focus:outline-none focus:ring-2 focus:ring-brand"
              />
              {market.searchActive && (
                <button
                  onClick={market.clearSearch}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-tx-3 hover:text-tx-1"
                  title="검색 해제"
                >
                  <X className="w-4 h-4" />
                </button>
              )}
            </div>
            <button
              onClick={market.handleSearch}
              disabled={market.loading}
              className="px-5 py-2 bg-brand text-brand-ink rounded-lg hover:bg-brand-dark disabled:opacity-50 flex items-center gap-1.5"
            >
              <Search className="w-4 h-4" />
              검색
            </button>
          </div>
          {market.searchActive && (
            <p className="mt-2 text-sm text-tx-2">
              "{market.searchKeyword}" 검색 결과 {market.searchResults.length}개
              {market.searchResults.length === 0 && ' — 보유 종목에 없습니다. 신규 종목 추가를 이용하세요.'}
            </p>
          )}
        </div>

        {/* 종목 목록 (검색 결과 or 전체) */}
        <AssetListSection
          assets={displayAssets}
          loading={market.loading}
          searchMode={market.searchActive}
          onRefresh={market.searchActive ? market.handleSearch : market.loadAssets}
          onDelete={market.handleDeleteAsset}
          onRestore={market.handleRestoreAsset}
          listActive={market.listActive}
          onListActiveChange={market.handleListActiveChange}
          onBulkDelete={market.handleBulkDelete}
          onUpdatePrice={market.handleUpdatePrice}
          onUpdateMarketCap={market.handleUpdateMarketCap}
          onUpdateName={market.handleUpdateName}
          onUpdateAllPrices={market.handleUpdateAllPrices}
          onUpdateAllMarketCaps={market.handleUpdateAllMarketCaps}
          onCollectNewSymbols={market.handleCollectNewSymbols}
          currentPage={market.currentPage}
          pageSize={market.pageSize}
          totalPages={market.totalPages}
          totalElements={market.totalElements}
          sortBy={market.sortBy}
          sortDirection={market.sortDirection}
          onPageChange={market.handlePageChange}
          onPageSizeChange={market.handlePageSizeChange}
          onSortChange={market.handleSortChange}
          selected={market.selected}
          onToggleOne={market.toggleOne}
          onToggleAll={market.toggleAll}
        />
      </div>

      {/* 신규 추가 모달 */}
      <AddAssetModal
        isOpen={addOpen}
        onClose={() => setAddOpen(false)}
        preview={market.preview}
        loading={market.loading}
        onPreview={market.handlePreview}
        onAdd={async () => {
          await market.handleAddAsset();
          setAddOpen(false);
        }}
        onResetPreview={market.resetPreview}
      />
    </div>
  );
}
