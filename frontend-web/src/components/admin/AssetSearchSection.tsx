import React from 'react';
import { Search } from 'lucide-react';
import type { Asset } from '@services/adminApi';

interface AssetSearchSectionProps {
  searchKeyword: string;
  searchResults: Asset[];
  loading: boolean;
  onSearchKeywordChange: (keyword: string) => void;
  onSearch: () => void;
  onPreview: (symbol: string) => void;
}

export default function AssetSearchSection({
  searchKeyword,
  searchResults,
  loading,
  onSearchKeywordChange,
  onSearch,
  onPreview,
}: AssetSearchSectionProps) {
  const handleKeyPress = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      onSearch();
    }
  };

  return (
    <div className="bg-white rounded-lg shadow-md p-6 mb-6">
      <h2 className="text-xl font-semibold mb-4">심볼 검색</h2>

      <div className="flex gap-4">
        <input
          type="text"
          value={searchKeyword}
          onChange={(e) => onSearchKeywordChange(e.target.value)}
          onKeyPress={handleKeyPress}
          placeholder="회사명 또는 티커 입력 (예: Tesla, AAPL)"
          className="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
        />
        <button
          onClick={onSearch}
          disabled={loading}
          className="px-6 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:opacity-50 flex items-center gap-2"
        >
          <Search className="w-5 h-5" />
          검색
        </button>
      </div>

      {searchResults.length > 0 && (
        <div className="mt-4 border-t pt-4">
          <h3 className="font-medium mb-2">검색 결과</h3>
          <div className="space-y-2">
            {searchResults.map((asset) => (
              <div
                key={asset.symbol}
                className="flex items-center justify-between p-3 bg-gray-50 rounded-lg hover:bg-gray-100 cursor-pointer transition"
                onClick={() => onPreview(asset.symbol)}
              >
                <div>
                  <span className="font-semibold text-indigo-600">{asset.symbol}</span>
                  <span className="ml-2 text-gray-700">{asset.name}</span>
                  <span className="ml-2 text-sm text-gray-500">({asset.assetType})</span>
                </div>
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    onPreview(asset.symbol);
                  }}
                  className="text-sm text-indigo-600 hover:text-indigo-800"
                >
                  미리보기
                </button>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
