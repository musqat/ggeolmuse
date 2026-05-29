import React from 'react';
import { ExternalLink } from 'lucide-react';
import StockSearchInput from '@/components/common/StockSearchInput';

interface OHLCData {
  open: number;
  high: number;
  low: number;
  close: number;
}

interface StockSelectionPanelProps {
  selectedStock: string;
  onStockSelect: (symbol: string) => void;
  supportedSymbols: string[];
  latestOHLC: OHLCData | null;
  onViewDetailedChart: () => void;
}

const StockSelectionPanel: React.FC<StockSelectionPanelProps> = ({
  selectedStock,
  onStockSelect,
  supportedSymbols,
  latestOHLC,
  onViewDetailedChart,
}) => {
  return (
    <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-semibold text-tx-1">종목 선택</h2>
        {selectedStock && (
          <button
            onClick={onViewDetailedChart}
            className="flex items-center space-x-1 text-brand hover:text-brand-dark text-sm"
          >
            <span>상세 차트</span>
            <ExternalLink className="w-4 h-4" />
          </button>
        )}
      </div>

      <StockSearchInput
        value={selectedStock}
        onChange={onStockSelect}
        supportedSymbols={supportedSymbols}
        placeholder="종목명 또는 티커 입력"
      />

      {latestOHLC && (
        <div className="mt-4 grid grid-cols-2 md:grid-cols-4 gap-3 p-4 bg-surface/50 rounded-lg">
          <div className="text-center">
            <p className="text-xs text-tx-2">시가</p>
            <p className="font-semibold text-tx-1">${latestOHLC.open.toFixed(2)}</p>
          </div>
          <div className="text-center">
            <p className="text-xs text-tx-2">고가</p>
            <p className="font-semibold text-green-600">${latestOHLC.high.toFixed(2)}</p>
          </div>
          <div className="text-center">
            <p className="text-xs text-tx-2">저가</p>
            <p className="font-semibold text-red-600">${latestOHLC.low.toFixed(2)}</p>
          </div>
          <div className="text-center">
            <p className="text-xs text-tx-2">종가</p>
            <p className="font-semibold text-tx-1">${latestOHLC.close.toFixed(2)}</p>
          </div>
        </div>
      )}
    </div>
  );
};

export default StockSelectionPanel;
