import React from 'react';
import { Plus } from 'lucide-react';
import type { CompanyOverview } from '@services/adminApi';

interface AssetPreviewSectionProps {
  preview: CompanyOverview;
  loading: boolean;
  onAdd: () => void;
}

export default function AssetPreviewSection({
  preview,
  loading,
  onAdd,
}: AssetPreviewSectionProps) {
  return (
    <div className="bg-surface rounded-lg shadow-md p-6 mb-6">
      <h2 className="text-xl font-semibold mb-4">심볼 미리보기</h2>

      <div className="grid grid-cols-2 gap-4 mb-4">
        <div>
          <p className="text-sm text-tx-2">심볼</p>
          <p className="font-semibold text-lg">{preview.symbol}</p>
        </div>
        <div>
          <p className="text-sm text-tx-2">회사명</p>
          <p className="font-semibold">{preview.name}</p>
        </div>
        <div>
          <p className="text-sm text-tx-2">거래소</p>
          <p>{preview.exchange}</p>
        </div>
        <div>
          <p className="text-sm text-tx-2">섹터</p>
          <p>{preview.sector}</p>
        </div>
        <div>
          <p className="text-sm text-tx-2">산업</p>
          <p>{preview.industry}</p>
        </div>
        <div>
          <p className="text-sm text-tx-2">시가총액</p>
          <p>${preview.marketCap?.toLocaleString()}</p>
        </div>
      </div>

      {preview.description && (
        <div className="mb-4">
          <p className="text-sm text-tx-2 mb-2">설명</p>
          <p className="text-sm text-tx-1 line-clamp-3">{preview.description}</p>
        </div>
      )}

      <button
        onClick={onAdd}
        disabled={loading}
        className="w-full py-3 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50 flex items-center justify-center gap-2 transition"
      >
        <Plus className="w-5 h-5" />
        심볼 추가 및 데이터 수집 시작
      </button>
    </div>
  );
}
