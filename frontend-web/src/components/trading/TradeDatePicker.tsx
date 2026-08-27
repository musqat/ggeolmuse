import React, { useState, useEffect } from 'react';
import type { CandlestickChartData } from '@/types/ohlc';
import DatePicker from '../common/DatePicker';

interface TradeDatePickerProps {
  tradeDate: string;
  onTradeDateChange: (date: string) => void;
  chartData: CandlestickChartData[];
  selectedDateOHLC: CandlestickChartData | null;
  onFindClosestPastDate: (targetDate: string) => CandlestickChartData | null;
}

const TradeDatePicker: React.FC<TradeDatePickerProps> = ({
  tradeDate,
  onTradeDateChange,
  chartData,
  selectedDateOHLC,
  onFindClosestPastDate,
}) => {
  const [tradeDateObj, setTradeDateObj] = useState<Date | null>(
    tradeDate ? new Date(tradeDate) : null
  );
  const [showTradeDatePicker, setShowTradeDatePicker] = useState(false);

  useEffect(() => {
    if (tradeDateObj) {
      onTradeDateChange(tradeDateObj.toISOString().split('T')[0]);
    }
  }, [tradeDateObj, onTradeDateChange]);

  // tradeDate 가 외부에서 변경되면 tradeDateObj 업데이트.
  useEffect(() => {
    if (!tradeDate) return;
    setTradeDateObj((prev) =>
      !prev || prev.toISOString().split('T')[0] !== tradeDate
        ? new Date(tradeDate)
        : prev
    );
  }, [tradeDate]);

  const handleSetLatest = () => {
    if (chartData.length > 0) {
      const latestDate = chartData[chartData.length - 1].time;
      setTradeDateObj(new Date(latestDate));
      onTradeDateChange(latestDate);
    }
  };

  const handleSetOneWeekAgo = () => {
    if (chartData.length === 0) return;
    const latest = new Date(chartData[chartData.length - 1].time);
    latest.setDate(latest.getDate() - 7);
    const closest = onFindClosestPastDate(latest.toISOString().split('T')[0]);
    if (closest) onTradeDateChange(closest.time);
  };

  const handleSetOneMonthAgo = () => {
    if (chartData.length === 0) return;
    const latest = new Date(chartData[chartData.length - 1].time);
    latest.setMonth(latest.getMonth() - 1);
    const closest = onFindClosestPastDate(latest.toISOString().split('T')[0]);
    if (closest) onTradeDateChange(closest.time);
  };

  const handleSetOldest = () => {
    if (chartData.length > 0) {
      onTradeDateChange(chartData[0].time);
    }
  };

  return (
    <div className="mb-4">
      <label className="block text-sm font-medium text-tx-1 mb-2">거래일</label>

      {/* Quick Select Buttons */}
      <div className="grid grid-cols-4 gap-2 mb-2">
        <button
          type="button"
          onClick={handleSetLatest}
          disabled={chartData.length === 0}
          className="px-2 py-1 text-xs bg-elevated text-tx-1 rounded hover:bg-hover disabled:bg-surface/50 disabled:text-tx-3 disabled:cursor-not-allowed transition-colors"
        >
          최신
        </button>
        <button
          type="button"
          onClick={handleSetOneWeekAgo}
          disabled={chartData.length === 0}
          className="px-2 py-1 text-xs bg-elevated text-tx-1 rounded hover:bg-hover disabled:bg-surface/50 disabled:text-tx-3 disabled:cursor-not-allowed transition-colors"
        >
          1주전
        </button>
        <button
          type="button"
          onClick={handleSetOneMonthAgo}
          disabled={chartData.length === 0}
          className="px-2 py-1 text-xs bg-elevated text-tx-1 rounded hover:bg-hover disabled:bg-surface/50 disabled:text-tx-3 disabled:cursor-not-allowed transition-colors"
        >
          1달전
        </button>
        <button
          type="button"
          onClick={handleSetOldest}
          disabled={chartData.length === 0}
          className="px-2 py-1 text-xs bg-elevated text-tx-1 rounded hover:bg-hover disabled:bg-surface/50 disabled:text-tx-3 disabled:cursor-not-allowed transition-colors"
        >
          가장오래된
        </button>
      </div>

      {/* Date Input */}
      <div className="relative">
        <button
          type="button"
          onClick={() => setShowTradeDatePicker(!showTradeDatePicker)}
          className="w-full px-3 py-1.5 text-sm border border-line-strong rounded-md text-left hover:border-indigo-500 focus:ring-2 focus:ring-brand focus:border-brand transition"
        >
          {tradeDateObj
            ? tradeDateObj.toLocaleDateString('ko-KR')
            : '날짜를 선택하세요'}
        </button>
        {showTradeDatePicker && (
          <div className="absolute top-full left-0 md:left-1/2 md:-translate-x-1/2 mt-2 z-50 shadow-2xl w-[400px] max-w-[calc(100vw-2rem)]">
            <DatePicker
              value={tradeDateObj}
              onChange={(date) => {
                setTradeDateObj(date);
                setShowTradeDatePicker(false);
              }}
            />
          </div>
        )}
      </div>

      {/* Selected Date OHLC Info */}
      {selectedDateOHLC && (
        <div className="mt-2 p-3 bg-brand-bg rounded-lg">
          <div className="text-xs text-brand font-medium mb-1">
            선택한 날짜: {selectedDateOHLC.time}
          </div>
          <div className="grid grid-cols-4 gap-2 text-xs">
            <div>
              <span className="text-tx-2">시가</span>
              <p className="font-semibold text-tx-1">${selectedDateOHLC.open.toFixed(2)}</p>
            </div>
            <div>
              <span className="text-tx-2">고가</span>
              <p className="font-semibold text-green-600">${selectedDateOHLC.high.toFixed(2)}</p>
            </div>
            <div>
              <span className="text-tx-2">저가</span>
              <p className="font-semibold text-red-600">${selectedDateOHLC.low.toFixed(2)}</p>
            </div>
            <div>
              <span className="text-tx-2">종가</span>
              <p className="font-semibold text-tx-1">${selectedDateOHLC.close.toFixed(2)}</p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default TradeDatePicker;
