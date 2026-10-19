import React from 'react';

// 잔액 정산을 기다리는 매수 표시
const TradePendingBadge: React.FC = () => (
  <span className="text-xs px-2 py-0.5 rounded border border-line text-tx-2">
    정산 중
  </span>
);

export default TradePendingBadge;
