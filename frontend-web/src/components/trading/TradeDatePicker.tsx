import React, { useState, useEffect } from 'react';
import type { CandlestickChartData } from '@/types/ohlc';
import DatePicker from '../common/DatePicker';

/**
 * 거래일 선택 컴포넌트의 Props 인터페이스
 */
interface TradeDatePickerProps {
  /** 선택된 거래일 (YYYY-MM-DD 형식) */
  tradeDate: string;
  /** 거래일 변경 핸들러 */
  onTradeDateChange: (date: string) => void;
  /** 차트 데이터 배열 */
  chartData: CandlestickChartData[];
  /** 선택된 날짜의 OHLC 데이터 */
  selectedDateOHLC: CandlestickChartData | null;
  /** 가장 가까운 과거 거래일을 찾는 함수 */
  onFindClosestPastDate: (targetDate: string) => CandlestickChartData | null;
}

/**
 * 거래일 선택 컴포넌트
 *
 * 빠른 선택 버튼(최신, 1주전, 1달전, 가장오래된)과 날짜 입력,
 * 선택된 날짜의 OHLC 데이터 미리보기를 제공합니다.
 */
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

  // tradeDate가 외부에서 변경되면 tradeDateObj 업데이트
  useEffect(() => {
    if (tradeDate && (!tradeDateObj || tradeDateObj.toISOString().split('T')[0] !== tradeDate)) {
      setTradeDateObj(new Date(tradeDate));
    }
  }, [tradeDate]);

  /**
   * 최신 거래일로 설정
   */
  const handleSetLatest = () => {
    if (chartData.length > 0) {
      const latestDate = chartData[chartData.length - 1].time;
      setTradeDateObj(new Date(latestDate));
      onTradeDateChange(latestDate);
    }
  };

  /**
   * 1주 전 거래일로 설정
   */
  const handleSetOneWeekAgo = () => {
    if (chartData.length === 0) return;
    const latest = new Date(chartData[chartData.length - 1].time);
    latest.setDate(latest.getDate() - 7);
    const closest = onFindClosestPastDate(latest.toISOString().split('T')[0]);
    if (closest) onTradeDateChange(closest.time);
  };

  /**
   * 1달 전 거래일로 설정
   */
  const handleSetOneMonthAgo = () => {
    if (chartData.length === 0) return;
    const latest = new Date(chartData[chartData.length - 1].time);
    latest.setMonth(latest.getMonth() - 1);
    const closest = onFindClosestPastDate(latest.toISOString().split('T')[0]);
    if (closest) onTradeDateChange(closest.time);
  };

  /**
   * 가장 오래된 거래일로 설정
   */
  const handleSetOldest = () => {
    if (chartData.length > 0) {
      onTradeDateChange(chartData[0].time);
    }
  };

  return (
    <div className="mb-4">
      <label className="block text-sm font-medium text-gray-700 mb-2">거래일</label>

      {/* Quick Select Buttons */}
      <div className="grid grid-cols-4 gap-2 mb-2">
        <button
          type="button"
          onClick={handleSetLatest}
          disabled={chartData.length === 0}
          className="px-2 py-1 text-xs bg-gray-100 text-gray-700 rounded hover:bg-gray-200 disabled:bg-gray-50 disabled:text-gray-400 disabled:cursor-not-allowed transition-colors"
        >
          최신
        </button>
        <button
          type="button"
          onClick={handleSetOneWeekAgo}
          disabled={chartData.length === 0}
          className="px-2 py-1 text-xs bg-gray-100 text-gray-700 rounded hover:bg-gray-200 disabled:bg-gray-50 disabled:text-gray-400 disabled:cursor-not-allowed transition-colors"
        >
          1주전
        </button>
        <button
          type="button"
          onClick={handleSetOneMonthAgo}
          disabled={chartData.length === 0}
          className="px-2 py-1 text-xs bg-gray-100 text-gray-700 rounded hover:bg-gray-200 disabled:bg-gray-50 disabled:text-gray-400 disabled:cursor-not-allowed transition-colors"
        >
          1달전
        </button>
        <button
          type="button"
          onClick={handleSetOldest}
          disabled={chartData.length === 0}
          className="px-2 py-1 text-xs bg-gray-100 text-gray-700 rounded hover:bg-gray-200 disabled:bg-gray-50 disabled:text-gray-400 disabled:cursor-not-allowed transition-colors"
        >
          가장오래된
        </button>
      </div>

      {/* Date Input */}
      <div className="relative">
        <button
          type="button"
          onClick={() => setShowTradeDatePicker(!showTradeDatePicker)}
          className="w-full px-3 py-1.5 text-sm border border-gray-300 rounded-md text-left hover:border-indigo-500 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition"
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
        <div className="mt-2 p-3 bg-indigo-50 rounded-lg">
          <div className="text-xs text-indigo-600 font-medium mb-1">
            선택한 날짜: {selectedDateOHLC.time}
          </div>
          <div className="grid grid-cols-4 gap-2 text-xs">
            <div>
              <span className="text-gray-500">시가</span>
              <p className="font-semibold text-gray-900">${selectedDateOHLC.open.toFixed(2)}</p>
            </div>
            <div>
              <span className="text-gray-500">고가</span>
              <p className="font-semibold text-green-600">${selectedDateOHLC.high.toFixed(2)}</p>
            </div>
            <div>
              <span className="text-gray-500">저가</span>
              <p className="font-semibold text-red-600">${selectedDateOHLC.low.toFixed(2)}</p>
            </div>
            <div>
              <span className="text-gray-500">종가</span>
              <p className="font-semibold text-gray-900">${selectedDateOHLC.close.toFixed(2)}</p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default TradeDatePicker;
