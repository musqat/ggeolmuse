import React from 'react';

// 고른 날이 휴장일이라 다른 거래일 종가로 산 경우의 알림
export const HolidayNotice: React.FC<{ label: string; chosen?: string | null; traded?: string | null }> = ({
  label,
  chosen,
  traded,
}) => {
  if (!chosen || !traded || chosen === traded) return null;
  return (
    <p className="text-sm text-warning bg-warning-soft/10 border border-warning-soft/40 rounded-lg px-3 py-2">
      선택한 {label}({chosen})은 휴장일이라 {traded > chosen ? '다음' : '그 전'} 거래일({traded}) 종가로 샀습니다.
    </p>
  );
};
