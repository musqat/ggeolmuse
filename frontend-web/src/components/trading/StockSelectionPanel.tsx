import React from 'react';
import { ExternalLink } from 'lucide-react';
import StockSearchInput from '@/components/common/StockSearchInput';

/**
 * OHLC 데이터 인터페이스
 */
interface OHLCData {
  open: number;
  high: number;
  low: number;
  close: number;
}

/**
 * 종목 선택 패널 컴포넌트의 Props 인터페이스
 */
interface StockSelectionPanelProps {
  /** 선택된 종목 심볼 */
  selectedStock: string;
  /** 종목 선택 핸들러 */
  onStockSelect: (symbol: string) => void;
  /** 지원되는 종목 심볼 목록 */
  supportedSymbols: string[];
  /** 최신 OHLC 데이터 */
  latestOHLC: OHLCData | null;
  /** 상세 차트 보기 핸들러 */
  onViewDetailedChart: () => void;
}

/**
 * 종목 선택 패널 컴포넌트
 *
 * 종목 검색 입력창과 OHLC 데이터 표시, 상세 차트 링크를 제공합니다.
 */
const StockSelectionPanel: React.FC<StockSelectionPanelProps> = ({
  selectedStock,
  onStockSelect,
  supportedSymbols,
  latestOHLC,
  onViewDetailedChart,
}) => {
  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-semibold text-gray-900">종목 선택</h2>
        {selectedStock && (
          <button
            onClick={onViewDetailedChart}
            className="flex items-center space-x-1 text-indigo-600 hover:text-indigo-700 text-sm"
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
        <div className="mt-4 grid grid-cols-2 md:grid-cols-4 gap-3 p-4 bg-gray-50 rounded-lg">
          <div className="text-center">
            <p className="text-xs text-gray-500">시가</p>
            <p className="font-semibold text-gray-900">${latestOHLC.open.toFixed(2)}</p>
          </div>
          <div className="text-center">
            <p className="text-xs text-gray-500">고가</p>
            <p className="font-semibold text-green-600">${latestOHLC.high.toFixed(2)}</p>
          </div>
          <div className="text-center">
            <p className="text-xs text-gray-500">저가</p>
            <p className="font-semibold text-red-600">${latestOHLC.low.toFixed(2)}</p>
          </div>
          <div className="text-center">
            <p className="text-xs text-gray-500">종가</p>
            <p className="font-semibold text-gray-900">${latestOHLC.close.toFixed(2)}</p>
          </div>
        </div>
      )}
    </div>
  );
};

export default StockSelectionPanel;
