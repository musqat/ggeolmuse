import React from 'react';
import type { PriceType } from '@/utils/priceUtils';

const PRICE_LABELS: Record<PriceType, string> = {
  open: '기준가 (시가)',
  high: '기준가 (고가)',
  low: '기준가 (저가)',
  close: '기준가 (종가)',
  limit: '지정가',
};

// 기준가(지정가면 입력 가격) × 수량 금액(실제 체결은 슬리피지 · 수수료를 더함, 지정가는 슬리피지 없음)
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
