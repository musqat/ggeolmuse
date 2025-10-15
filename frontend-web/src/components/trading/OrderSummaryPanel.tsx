import React from 'react';

/**
 * 주문 요약 패널
 *
 * 주문 내역을 요약해서 보여주는 패널 컴포넌트입니다.
 * 종목, 거래일, 수량, 체결가, 총액 정보를 표시합니다.
 *
 * @param selectedStock - 선택된 종목 심볼
 * @param tradeDate - 거래일
 * @param quantity - 주문 수량
 * @param currentPrice - 체결 가격
 * @param totalAmount - 총 주문 금액
 */
interface OrderSummaryPanelProps {
  selectedStock: string;
  tradeDate: string;
  quantity: string;
  currentPrice: number;
  totalAmount: number;
}

const OrderSummaryPanel: React.FC<OrderSummaryPanelProps> = ({
  selectedStock,
  tradeDate,
  quantity,
  currentPrice,
  totalAmount,
}) => {
  return (
    <div className="mb-6 p-4 bg-gray-50 rounded-lg space-y-2">
      <div className="flex justify-between text-sm">
        <span className="text-gray-600">종목</span>
        <span className="font-medium">{selectedStock}</span>
      </div>
      <div className="flex justify-between text-sm">
        <span className="text-gray-600">거래일</span>
        <span className="font-medium">{tradeDate}</span>
      </div>
      <div className="flex justify-between text-sm">
        <span className="text-gray-600">수량</span>
        <span className="font-medium">{quantity}주</span>
      </div>
      <div className="flex justify-between text-sm">
        <span className="text-gray-600">체결가</span>
        <span className="font-medium">${currentPrice.toFixed(2)}</span>
      </div>
      <div className="border-t pt-2 mt-2">
        <div className="flex justify-between font-semibold">
          <span>총액</span>
          <span>${totalAmount.toFixed(2)}</span>
        </div>
      </div>
    </div>
  );
};

export default OrderSummaryPanel;
