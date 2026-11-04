import React from 'react';
import { DEFAULT_FX_RATE } from '@components/charts/backtest/shared/fxRates';
import { formatNumber } from '../../../utils/format';

// 앞에서부터 보이는 날짜 수
const SHOWN = 3;

// 환율 기록이 없어 기본 환율로 계산한 날이 있을 때의 알림
export const DefaultFxNotice: React.FC<{ dates?: string[] | null }> = ({ dates }) => {
  if (!dates || dates.length === 0) return null;
  const shown = dates.slice(0, SHOWN).join(' · ');
  const rest = dates.length - SHOWN;
  return (
    <p role="note" className="text-sm text-warning bg-warning-soft/10 border border-warning-soft/40 rounded-lg px-3 py-2">
      환율 기록이 없어 기본 환율 ₩{formatNumber(DEFAULT_FX_RATE)} 으로 계산한 날이 있습니다: {shown}
      {rest > 0 && ` 외 ${rest}일`}
    </p>
  );
};
