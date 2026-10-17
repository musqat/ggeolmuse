import React from 'react';
import type { PriceType, OHLCData } from '@/utils/priceUtils';

// 지정가면 가격 입력 칸과 당일 OHLC 범위를 보여 줌
interface PriceTypeSelectorProps {
  priceType: PriceType;
  onPriceTypeChange: (priceType: PriceType) => void;
  limitPrice: string;
  onLimitPriceChange: (price: string) => void;
  ohlcData: OHLCData | null;
}

const PriceTypeSelector: React.FC<PriceTypeSelectorProps> = ({
  priceType,
  onPriceTypeChange,
  limitPrice,
  onLimitPriceChange,
  ohlcData,
}) => {
  return (
    <>
      {/* Price Type */}
      <div className="mb-4">
        <label className="block text-sm font-medium text-tx-1 mb-2">주문 유형</label>
        <select
          value={priceType}
          onChange={(e) => onPriceTypeChange(e.target.value as PriceType)}
          className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
        >
          <option value="open">시가</option>
          <option value="high">고가</option>
          <option value="low">저가</option>
          <option value="close">종가</option>
          <option value="limit">지정가</option>
        </select>
      </div>

      {/* Limit Price Input */}
      {priceType === 'limit' && ohlcData && (
        <div className="mb-4">
          <label className="block text-sm font-medium text-tx-1 mb-2">
            지정가 (${ohlcData.low.toFixed(2)} ~ ${ohlcData.high.toFixed(2)})
          </label>
          <input
            type="number"
            value={limitPrice}
            onChange={(e) => onLimitPriceChange(e.target.value)}
            className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
            placeholder="0.00"
            step="0.01"
            min={ohlcData.low}
            max={ohlcData.high}
          />
        </div>
      )}
    </>
  );
};

export default PriceTypeSelector;
