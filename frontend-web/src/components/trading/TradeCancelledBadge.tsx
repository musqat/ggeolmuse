import React from 'react';
import { cancelReasonLabel } from '../../utils/tradeStatus';

interface TradeCancelledBadgeProps {
  reason?: string | null;
}

// 잔액 반영 실패로 취소된 체결 표시
const TradeCancelledBadge: React.FC<TradeCancelledBadgeProps> = ({ reason }) => (
  <span className="text-xs px-2 py-0.5 rounded border border-line text-tx-2">
    취소됨 · {cancelReasonLabel(reason)}
  </span>
);

export default TradeCancelledBadge;
