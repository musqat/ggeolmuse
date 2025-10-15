import React from 'react';

/**
 * OHLC 데이터 인터페이스
 */
interface OHLCData {
  open: number;
  high: number;
  low: number;
  close: number;
}

/**
 * OHLC 표시 그리드 컴포넌트의 Props 인터페이스
 */
interface OHLCDisplayGridProps {
  /** OHLC 데이터 */
  ohlc: OHLCData;
  /** 표시 스타일 변형 */
  variant?: 'default' | 'compact';
  /** 라벨 표시 여부 (기본값: true) */
  showLabel?: boolean;
}

/**
 * OHLC 데이터를 4개 그리드로 표시하는 재사용 가능한 컴포넌트
 *
 * 시가(Open), 고가(High), 저가(Low), 종가(Close)를 2x2 그리드로 표시합니다.
 */
const OHLCDisplayGrid: React.FC<OHLCDisplayGridProps> = ({
  ohlc,
  variant = 'default',
  showLabel = true,
}) => {
  const isCompact = variant === 'compact';

  return (
    <div
      className={`grid grid-cols-2 md:grid-cols-4 gap-3 ${
        isCompact ? 'p-3' : 'p-4'
      } bg-gray-50 rounded-lg`}
    >
      {/* 시가 */}
      <div className="text-center">
        {showLabel && (
          <p className={`text-gray-500 ${isCompact ? 'text-xs' : 'text-xs'}`}>
            시가
          </p>
        )}
        <p className={`font-semibold text-gray-900 ${isCompact ? 'text-sm' : ''}`}>
          ${ohlc.open.toFixed(2)}
        </p>
      </div>

      {/* 고가 */}
      <div className="text-center">
        {showLabel && (
          <p className={`text-gray-500 ${isCompact ? 'text-xs' : 'text-xs'}`}>
            고가
          </p>
        )}
        <p className={`font-semibold text-green-600 ${isCompact ? 'text-sm' : ''}`}>
          ${ohlc.high.toFixed(2)}
        </p>
      </div>

      {/* 저가 */}
      <div className="text-center">
        {showLabel && (
          <p className={`text-gray-500 ${isCompact ? 'text-xs' : 'text-xs'}`}>
            저가
          </p>
        )}
        <p className={`font-semibold text-red-600 ${isCompact ? 'text-sm' : ''}`}>
          ${ohlc.low.toFixed(2)}
        </p>
      </div>

      {/* 종가 */}
      <div className="text-center">
        {showLabel && (
          <p className={`text-gray-500 ${isCompact ? 'text-xs' : 'text-xs'}`}>
            종가
          </p>
        )}
        <p className={`font-semibold text-gray-900 ${isCompact ? 'text-sm' : ''}`}>
          ${ohlc.close.toFixed(2)}
        </p>
      </div>
    </div>
  );
};

export default OHLCDisplayGrid;
