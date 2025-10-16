import React, { useState, useEffect } from 'react';
import { Calendar } from 'lucide-react';
import CandlestickChart from '@/components/charts/trading/CandlestickChart';
import type { CandlestickChartData } from '@/types/ohlc';
import type { Timeframe } from '@/utils/dateUtils';
import DatePicker from '../common/DatePicker';

/**
 * 거래 차트 섹션 컴포넌트의 Props 인터페이스
 */
interface TradingChartSectionProps {
  /** 선택된 종목 심볼 */
  selectedStock: string;
  /** 차트 데이터 */
  chartData: CandlestickChartData[];
  /** 차트 로딩 상태 */
  chartLoading: boolean;
  /** 선택된 기간 */
  timeframe: Timeframe;
  /** 기간 변경 핸들러 */
  onTimeframeChange: (timeframe: Timeframe) => void;
  /** 직접설정 시작일 */
  customStartDate?: string;
  /** 직접설정 종료일 */
  customEndDate?: string;
  /** 직접설정 시작일 변경 핸들러 */
  onCustomStartDateChange?: (date: string) => void;
  /** 직접설정 종료일 변경 핸들러 */
  onCustomEndDateChange?: (date: string) => void;
}

/**
 * 사용 가능한 기간 옵션
 */
const TIMEFRAMES: Timeframe[] = ['1주', '1개월', '3개월', '6개월', '1년', '직접설정'];

/**
 * 거래 차트 섹션 컴포넌트
 *
 * 차트 표시와 기간 선택 컨트롤을 제공합니다.
 */
const TradingChartSection: React.FC<TradingChartSectionProps> = ({
  selectedStock,
  chartData,
  chartLoading,
  timeframe,
  onTimeframeChange,
  customStartDate,
  customEndDate,
  onCustomStartDateChange,
  onCustomEndDateChange,
}) => {
  const [startDateObj, setStartDateObj] = useState<Date | null>(
    customStartDate ? new Date(customStartDate) : null
  );
  const [endDateObj, setEndDateObj] = useState<Date | null>(
    customEndDate ? new Date(customEndDate) : null
  );
  const [showStartDatePicker, setShowStartDatePicker] = useState(false);
  const [showEndDatePicker, setShowEndDatePicker] = useState(false);

  useEffect(() => {
    if (startDateObj) {
      onCustomStartDateChange?.(startDateObj.toISOString().split('T')[0]);
    }
  }, [startDateObj, onCustomStartDateChange]);

  useEffect(() => {
    if (endDateObj) {
      onCustomEndDateChange?.(endDateObj.toISOString().split('T')[0]);
    }
  }, [endDateObj, onCustomEndDateChange]);

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-lg font-semibold text-gray-900">차트</h3>
        <div className="flex items-center space-x-2">
          <span className="text-sm font-medium text-gray-700">기간:</span>
          {TIMEFRAMES.map((tf) => (
            <button
              key={tf}
              onClick={() => onTimeframeChange(tf)}
              className={`px-3 py-1 text-sm rounded-md transition-colors flex items-center space-x-1 ${
                timeframe === tf
                  ? 'bg-indigo-600 text-white'
                  : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
              }`}
            >
              {tf === '직접설정' && <Calendar className="w-3 h-3" />}
              <span>{tf}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Custom Date Inputs */}
      {timeframe === '직접설정' && (
        <div className="flex items-center space-x-2 mb-4 p-3 bg-gray-50 rounded-lg">
          <div className="relative">
            <button
              type="button"
              onClick={() => {
                setShowStartDatePicker(!showStartDatePicker);
                setShowEndDatePicker(false);
              }}
              className="px-3 py-1.5 text-sm border border-gray-300 rounded-md text-left hover:border-indigo-500 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition whitespace-nowrap"
            >
              {startDateObj
                ? startDateObj.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
                : '시작일'}
            </button>
            {showStartDatePicker && (
              <div className="absolute top-full left-0 md:left-1/2 md:-translate-x-1/2 mt-2 z-50 shadow-2xl w-[400px] max-w-[calc(100vw-2rem)]">
                <DatePicker
                  value={startDateObj}
                  onChange={(date) => {
                    setStartDateObj(date);
                    setShowStartDatePicker(false);
                  }}
                />
              </div>
            )}
          </div>
          <span className="text-gray-400">~</span>
          <div className="relative">
            <button
              type="button"
              onClick={() => {
                setShowEndDatePicker(!showEndDatePicker);
                setShowStartDatePicker(false);
              }}
              className="px-3 py-1.5 text-sm border border-gray-300 rounded-md text-left hover:border-indigo-500 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition whitespace-nowrap"
            >
              {endDateObj
                ? endDateObj.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
                : '종료일'}
            </button>
            {showEndDatePicker && (
              <div className="absolute top-full left-0 md:left-1/2 md:-translate-x-1/2 mt-2 z-50 shadow-2xl w-[400px] max-w-[calc(100vw-2rem)]">
                <DatePicker
                  value={endDateObj}
                  onChange={(date) => {
                    setEndDateObj(date);
                    setShowEndDatePicker(false);
                  }}
                />
              </div>
            )}
          </div>
        </div>
      )}

      {/* Chart Display */}
      <div className="h-64">
        {chartLoading ? (
          <div className="h-full flex items-center justify-center">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div>
          </div>
        ) : chartData.length > 0 ? (
          <CandlestickChart data={chartData} symbol={selectedStock} className="h-full" />
        ) : (
          <div className="h-full flex items-center justify-center text-gray-400">
            차트 데이터가 없습니다
          </div>
        )}
      </div>
    </div>
  );
};

export default TradingChartSection;
