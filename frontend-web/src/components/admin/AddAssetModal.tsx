import React, { useState } from 'react';
import ReactDOM from 'react-dom';
import { X, Plus, Search } from 'lucide-react';
import type { CompanyOverview } from '@services/adminApi';

interface AddAssetModalProps {
  isOpen: boolean;
  onClose: () => void;
  preview: CompanyOverview | null;
  loading: boolean;
  // 티커로 외부 회사정보 조회 (없으면 에러)
  onPreview: (symbol: string) => void;
  onAdd: () => void;
  // preview 초기화 (모달 닫거나 티커 바꿀 때)
  onResetPreview: () => void;
}

const AddAssetModal: React.FC<AddAssetModalProps> = ({
  isOpen,
  onClose,
  preview,
  loading,
  onPreview,
  onAdd,
  onResetPreview,
}) => {
  const [ticker, setTicker] = useState('');

  if (!isOpen) return null;

  const handleClose = () => {
    setTicker('');
    onResetPreview();
    onClose();
  };

  const handleLookup = () => {
    const t = ticker.trim().toUpperCase();
    if (t) onPreview(t);
  };

  return ReactDOM.createPortal(
    <div
      style={{ position: 'fixed', inset: 0, zIndex: 9999, backgroundColor: 'rgba(0,0,0,0.5)' }}
      className="flex items-start justify-center p-4 overflow-y-auto"
      onClick={handleClose}
    >
      <div
        className="bg-surface rounded-lg shadow-xl w-full max-w-lg mt-16"
        onClick={(e) => e.stopPropagation()}
      >
        {/* 헤더 */}
        <div className="flex items-center justify-between p-4 border-b border-line">
          <h2 className="text-lg font-bold text-tx-1">신규 종목 추가</h2>
          <button onClick={handleClose} className="text-tx-3 hover:text-tx-1">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-4 space-y-4">
          <p className="text-sm text-tx-2">
            스크리너에 아직 없는 종목(신규 상장 등)을 티커로 외부에서 조회해 추가합니다.
          </p>

          {/* 티커 입력 */}
          <div className="flex gap-2">
            <input
              type="text"
              value={ticker}
              onChange={(e) => {
                setTicker(e.target.value.toUpperCase());
                if (preview) onResetPreview();
              }}
              onKeyDown={(e) => e.key === 'Enter' && handleLookup()}
              placeholder="티커 입력 (예: NVDA)"
              className="flex-1 px-3 py-2 border border-line-strong rounded-lg focus:outline-none focus:ring-2 focus:ring-brand"
              autoFocus
            />
            <button
              onClick={handleLookup}
              disabled={loading || !ticker.trim()}
              className="px-4 py-2 bg-brand text-white rounded-lg hover:bg-brand-dark disabled:opacity-50 flex items-center gap-1.5"
            >
              <Search className="w-4 h-4" />
              조회
            </button>
          </div>

          {/* 외부 조회 결과 */}
          {preview && (
            <div className="border border-line rounded-lg p-4 bg-surface/50">
              <div className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <p className="text-tx-2">심볼</p>
                  <p className="font-semibold text-brand">{preview.symbol}</p>
                </div>
                <div>
                  <p className="text-tx-2">회사명</p>
                  <p className="font-semibold text-tx-1">{preview.name}</p>
                </div>
                <div>
                  <p className="text-tx-2">거래소</p>
                  <p className="text-tx-1">{preview.exchange || '-'}</p>
                </div>
                <div>
                  <p className="text-tx-2">섹터</p>
                  <p className="text-tx-1">{preview.sector || '-'}</p>
                </div>
                <div>
                  <p className="text-tx-2">산업</p>
                  <p className="text-tx-1">{preview.industry || '-'}</p>
                </div>
                <div>
                  <p className="text-tx-2">시가총액</p>
                  <p className="text-tx-1">
                    {preview.marketCap ? `$${preview.marketCap.toLocaleString()}` : '-'}
                  </p>
                </div>
              </div>
              {preview.description && (
                <p className="mt-3 text-xs text-tx-2 line-clamp-3">{preview.description}</p>
              )}
              <button
                onClick={onAdd}
                disabled={loading}
                className="mt-4 w-full py-2.5 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50 flex items-center justify-center gap-2"
              >
                <Plus className="w-5 h-5" />
                추가 및 데이터 수집 시작
              </button>
            </div>
          )}

          {!preview && !loading && (
            <p className="text-xs text-tx-3">
              티커를 입력하고 조회하세요. 외부 데이터 소스에 존재하면 회사 정보가 표시됩니다.
            </p>
          )}
        </div>
      </div>
    </div>,
    document.body
  );
};

export default AddAssetModal;
