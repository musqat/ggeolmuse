import React from 'react';
import { SOLID_FILL } from '@/styles/buttons';

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
            ? `${SOLID_FILL.confirm} shadow-sm`
            : 'text-tx-2'
        }`}
      >
        매수
      </button>
      <button
        onClick={() => setOrderType('sell')}
        className={`flex-1 py-2 rounded-md font-medium transition-colors ${
          orderType === 'sell'
            ? `${SOLID_FILL.danger} shadow-sm`
            : 'text-tx-2'
        }`}
      >
        매도
      </button>
    </div>
  );
};

export default OrderTypeToggle;
