import React from 'react';
import type { PriceType, OHLCData } from '@/utils/priceUtils';

/**
 * 가격 유형 선택 컴포넌트
 *
 * 주문 가격 유형(시가/고가/저가/종가/지정가)을 선택할 수 있는 드롭다운과
 * 지정가 선택 시 가격 입력 필드를 제공하는 컴포넌트입니다.
 *
 * @param priceType - 현재 선택된 가격 유형
 * @param onPriceTypeChange - 가격 유형 변경 시 호출되는 콜백 함수
 * @param limitPrice - 지정가 입력값
 * @param onLimitPriceChange - 지정가 변경 시 호출되는 콜백 함수
 * @param ohlcData - 당일 OHLC 데이터 (지정가 범위 표시용)
 */
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
        <label className="block text-sm font-medium text-gray-700 mb-2">주문 유형</label>
        <select
          value={priceType}
          onChange={(e) => onPriceTypeChange(e.target.value as PriceType)}
          className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
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
          <label className="block text-sm font-medium text-gray-700 mb-2">
            지정가 (${ohlcData.low.toFixed(2)} ~ ${ohlcData.high.toFixed(2)})
          </label>
          <input
            type="number"
            value={limitPrice}
            onChange={(e) => onLimitPriceChange(e.target.value)}
            className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
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
