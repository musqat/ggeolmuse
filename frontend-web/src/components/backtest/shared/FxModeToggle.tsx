import React from 'react';
import { DollarSign } from 'lucide-react';

interface FxModeToggleProps {
  fxMode: 'auto' | 'manual';
  setFxMode: (mode: 'auto' | 'manual') => void;
  manualPurchaseFxRate: string;
  setManualPurchaseFxRate: (rate: string) => void;
  manualCurrentFxRate: string;
  setManualCurrentFxRate: (rate: string) => void;
  purchaseLabel?: string;
  currentLabel?: string;
}

export const FxModeToggle: React.FC<FxModeToggleProps> = ({
  fxMode,
  setFxMode,
  manualPurchaseFxRate,
  setManualPurchaseFxRate,
  manualCurrentFxRate,
  setManualCurrentFxRate,
  purchaseLabel = '시작일 환율',
  currentLabel = '현재 환율',
}) => {
  return (
    <div className="border-t border-line pt-4">
      <div className="flex items-center justify-between mb-3">
        <label className="flex items-center space-x-2 text-sm font-medium text-tx-1">
          <DollarSign className="w-4 h-4" />
          <span>환율 설정</span>
        </label>
        <div className="flex items-center space-x-2">
          <button
            type="button"
            onClick={() => setFxMode('auto')}
            className={`px-3 py-1.5 text-sm rounded-md transition-colors ${
              fxMode === 'auto'
                ? 'bg-brand text-white'
                : 'bg-elevated text-tx-1 hover:bg-hover'
            }`}
          >
            자동
          </button>
          <button
            type="button"
            onClick={() => setFxMode('manual')}
            className={`px-3 py-1.5 text-sm rounded-md transition-colors ${
              fxMode === 'manual'
                ? 'bg-brand text-white'
                : 'bg-elevated text-tx-1 hover:bg-hover'
            }`}
          >
            수동
          </button>
        </div>
      </div>

      {fxMode === 'auto' ? (
        <div className="p-3 bg-elevated/50 border border-brand/30 rounded-md">
          <p className="text-sm text-brand/90">
            시작일과 현재일의 실제 USD/KRW 환율을 자동으로 사용합니다.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-tx-1 mb-2">
              {purchaseLabel}
            </label>
            <div className="relative">
              <input
                type="number"
                value={manualPurchaseFxRate}
                onChange={(e) => setManualPurchaseFxRate(e.target.value)}
                className="w-full border border-line-strong rounded-md px-3 py-2 pr-12 focus:ring-2 focus:ring-brand focus:border-brand"
                placeholder="1300"
                step="0.01"
              />
              <span className="absolute right-3 top-2.5 text-tx-2 text-sm">
                KRW
              </span>
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-tx-1 mb-2">
              {currentLabel}
            </label>
            <div className="relative">
              <input
                type="number"
                value={manualCurrentFxRate}
                onChange={(e) => setManualCurrentFxRate(e.target.value)}
                className="w-full border border-line-strong rounded-md px-3 py-2 pr-12 focus:ring-2 focus:ring-brand focus:border-brand"
                placeholder="1350"
                step="0.01"
              />
              <span className="absolute right-3 top-2.5 text-tx-2 text-sm">
                KRW
              </span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
