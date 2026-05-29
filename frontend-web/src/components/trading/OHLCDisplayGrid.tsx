import React from 'react';

interface OHLCData {
  open: number;
  high: number;
  low: number;
  close: number;
}

interface OHLCDisplayGridProps {
  ohlc: OHLCData;
  variant?: 'default' | 'compact';
  showLabel?: boolean;
}

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
      } bg-surface/50 rounded-lg`}
    >
      {/* 시가 */}
      <div className="text-center">
        {showLabel && (
          <p className={`text-tx-2 ${isCompact ? 'text-xs':'text-xs'}`}>
            시가
          </p>
        )}
        <p className={`font-semibold text-tx-1 ${isCompact ? 'text-sm':''}`}>
          ${ohlc.open.toFixed(2)}
        </p>
      </div>

      {/* 고가 */}
      <div className="text-center">
        {showLabel && (
          <p className={`text-tx-2 ${isCompact ? 'text-xs':'text-xs'}`}>
            고가
          </p>
        )}
        <p className={`font-semibold text-green-600 ${isCompact ? 'text-sm':''}`}>
          ${ohlc.high.toFixed(2)}
        </p>
      </div>

      {/* 저가 */}
      <div className="text-center">
        {showLabel && (
          <p className={`text-tx-2 ${isCompact ? 'text-xs':'text-xs'}`}>
            저가
          </p>
        )}
        <p className={`font-semibold text-red-600 ${isCompact ? 'text-sm':''}`}>
          ${ohlc.low.toFixed(2)}
        </p>
      </div>

      {/* 종가 */}
      <div className="text-center">
        {showLabel && (
          <p className={`text-tx-2 ${isCompact ? 'text-xs':'text-xs'}`}>
            종가
          </p>
        )}
        <p className={`font-semibold text-tx-1 ${isCompact ? 'text-sm':''}`}>
          ${ohlc.close.toFixed(2)}
        </p>
      </div>
    </div>
  );
};

export default OHLCDisplayGrid;
