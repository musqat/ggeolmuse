import React from 'react';
import type { PriceType } from '@/utils/priceUtils';

const PRICE_LABELS: Record<PriceType, string> = {
  open: '기준가 (시가)',
  high: '기준가 (고가)',
  low: '기준가 (저가)',
  close: '기준가 (종가)',
  limit: '지정가',
};

/**
 * 주문 요약 패널
 *
 * 주문 내역을 요약해서 보여주는 패널 컴포넌트입니다.
 * 종목, 거래일, 수량, 기준가, 주문 금액을 표시합니다.
 * 실제 체결에는 슬리피지와 수수료가 붙고, 지정가에는 슬리피지가 붙지 않습니다.
 *
 * @param selectedStock - 선택된 종목 심볼
 * @param tradeDate - 거래일
 * @param quantity - 주문 수량
 * @param priceType - 가격 유형
 * @param currentPrice - 기준가 (지정가면 입력한 가격)
 * @param totalAmount - 주문 금액 (기준가 × 수량)
 */
interface OrderSummaryPanelProps {
  selectedStock: string;
  tradeDate: string;
  quantity: string;
  priceType: PriceType;
  currentPrice: number;
  totalAmount: number;
}

const OrderSummaryPanel: React.FC<OrderSummaryPanelProps> = ({
  selectedStock,
  tradeDate,
  quantity,
  priceType,
  currentPrice,
  totalAmount,
}) => {
  const note = priceType === 'limit'
    ? '수수료가 붙어 실제 금액은 달라질 수 있습니다.'
    : '슬리피지·수수료가 붙어 실제 금액은 달라질 수 있습니다.';

  return (
    <div className="mb-6 p-4 bg-surface/50 rounded-lg space-y-2">
      <div className="flex justify-between text-sm">
        <span className="text-tx-2">종목</span>
        <span className="font-medium">{selectedStock}</span>
      </div>
      <div className="flex justify-between text-sm">
        <span className="text-tx-2">거래일</span>
        <span className="font-medium">{tradeDate}</span>
      </div>
      <div className="flex justify-between text-sm">
        <span className="text-tx-2">수량</span>
        <span className="font-medium">{quantity}주</span>
      </div>
      <div className="flex justify-between text-sm">
        <span className="text-tx-2">{PRICE_LABELS[priceType]}</span>
        <span className="font-medium">${currentPrice.toFixed(2)}</span>
      </div>
      <div className="border-t pt-2 mt-2">
        <div className="flex justify-between font-semibold">
          <span>주문 금액</span>
          <span>${totalAmount.toFixed(2)}</span>
        </div>
        <p className="mt-1 text-xs text-tx-3">{note}</p>
      </div>
    </div>
  );
};

export default OrderSummaryPanel;
