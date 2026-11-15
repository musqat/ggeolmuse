import React from 'react';

// 배당 기록을 받지 못해 배당 없이 계산한 종목이 있을 때의 알림
export const DividendUnavailableNotice: React.FC<{ symbols?: string[] | null }> = ({ symbols }) => {
  if (!symbols || symbols.length === 0) return null;
  return (
    <p role="note" className="text-sm text-warning bg-warning-soft/10 border border-warning-soft/40 rounded-lg px-3 py-2">
      배당 기록을 받지 못해 배당 없이 계산한 종목: {symbols.join(' · ')}
    </p>
  );
};
