import React from 'react';

interface OrderTypeToggleProps {
  orderType: 'buy' | 'sell';
  setOrderType: (type: 'buy' | 'sell') => void;
}

// 매수 · 매도 토글
const OrderTypeToggle: React.FC<OrderTypeToggleProps> = ({ orderType, setOrderType }) => {
  return (
    <div className="flex rounded-lg bg-elevated p-1 mb-6">
      <button
        onClick={() => setOrderType('buy')}
        className={`flex-1 py-2 rounded-md font-medium transition-colors ${
          orderType === 'buy'
            ? 'bg-green-700 text-white shadow-sm'
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
