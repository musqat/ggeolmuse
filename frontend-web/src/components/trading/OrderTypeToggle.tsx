import React from 'react';

/**
 * 주문 타입 선택 토글 버튼
 *
 * 매수/매도 주문 타입을 선택할 수 있는 토글 버튼 컴포넌트입니다.
 *
 * @param orderType - 현재 선택된 주문 타입 ('buy' | 'sell')
 * @param setOrderType - 주문 타입을 변경하는 함수
 */
interface OrderTypeToggleProps {
  orderType: 'buy' | 'sell';
  setOrderType: (type: 'buy' | 'sell') => void;
}

const OrderTypeToggle: React.FC<OrderTypeToggleProps> = ({ orderType, setOrderType }) => {
  return (
    <div className="flex rounded-lg bg-elevated p-1 mb-6">
      <button
        onClick={() => setOrderType('buy')}
        className={`flex-1 py-2 rounded-md font-medium transition-colors ${
          orderType === 'buy'
            ? 'bg-green-600 text-white shadow-sm'
            : 'text-tx-2'
        }`}
      >
        매수
      </button>
      <button
        onClick={() => setOrderType('sell')}
        className={`flex-1 py-2 rounded-md font-medium transition-colors ${
          orderType === 'sell'
            ? 'bg-red-600 text-white shadow-sm'
            : 'text-tx-2'
        }`}
      >
        매도
      </button>
    </div>
  );
};

export default OrderTypeToggle;
