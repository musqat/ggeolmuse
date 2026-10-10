import React from 'react';

// 이 날짜 앞은 환율 자료가 비어 있을 수 있다
const FX_DATA_FROM = '2014-01-01';

// 시작일이 2014년 이전이면 수동 환율을 권한다. 아니면 아무것도 그리지 않는다
export const FxDataWarning: React.FC<{ startDate: string; className?: string }> = ({ startDate, className }) => {
  if (!startDate || startDate >= FX_DATA_FROM) return null;
  return (
    <div className={`flex items-start gap-2 p-3 bg-warning-soft/10 border border-warning-soft/40 rounded-lg${className ? ` ${className}` : ''}`}>
      <div className="flex-1">
        <p className="text-sm font-medium text-warning">
          환율 데이터 부족 가능성
        </p>
        <p className="text-xs text-tx-2 mt-1">
          2014년 이전 기간은 환율 정보가 부족할 수 있습니다.
          정확한 백테스트를 위해 <span className="font-semibold">수동 환율 입력</span>을 권장합니다.
        </p>
      </div>
    </div>
  );
};
