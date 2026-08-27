import React, { useState, useEffect } from 'react';
import { Calendar } from 'lucide-react';
import CandlestickChart from '@/components/charts/trading/CandlestickChart';
import type { CandlestickChartData } from '@/types/ohlc';
import type { Timeframe } from '@/utils/dateUtils';
import DatePicker from '../common/DatePicker';

interface TradingChartSectionProps {
  chartData: CandlestickChartData[];
  chartLoading: boolean;
  timeframe: Timeframe;
  onTimeframeChange: (timeframe: Timeframe) => void;
  customStartDate?: string;
  customEndDate?: string;
  onCustomStartDateChange?: (date: string) => void;
  onCustomEndDateChange?: (date: string) => void;
}

const TIMEFRAMES: Timeframe[] = ['1주', '1개월', '3개월', '6개월', '1년', '전체', '직접설정'];

const TradingChartSection: React.FC<TradingChartSectionProps> = ({
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
    <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-3 sm:p-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 mb-4">
        <h3 className="text-lg font-semibold text-tx-1">차트</h3>
        <div className="flex flex-wrap items-center gap-1.5 sm:gap-2">
          <span className="text-sm font-medium text-tx-1">기간:</span>
          {TIMEFRAMES.map((tf) => (
            <button
              key={tf}
              onClick={() => onTimeframeChange(tf)}
              className={`px-3 py-1 text-sm rounded-md transition-colors flex items-center space-x-1 ${
                timeframe === tf
                  ? 'bg-brand text-white'
                  : 'bg-elevated text-tx-1 hover:bg-hover'
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
        <div className="flex flex-wrap items-center gap-2 mb-4 p-3 bg-surface/50 rounded-lg">
          <div className="relative">
            <button
              type="button"
              onClick={() => {
                setShowStartDatePicker(!showStartDatePicker);
                setShowEndDatePicker(false);
              }}
              className="px-3 py-1.5 text-sm border border-line-strong rounded-md text-left hover:border-indigo-500 focus:ring-2 focus:ring-brand focus:border-brand transition whitespace-nowrap"
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
          <span className="text-tx-3">~</span>
          <div className="relative">
            <button
              type="button"
              onClick={() => {
                setShowEndDatePicker(!showEndDatePicker);
                setShowStartDatePicker(false);
              }}
              className="px-3 py-1.5 text-sm border border-line-strong rounded-md text-left hover:border-indigo-500 focus:ring-2 focus:ring-brand focus:border-brand transition whitespace-nowrap"
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
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-brand"></div>
          </div>
        ) : chartData.length > 0 ? (
          <CandlestickChart data={chartData} className="h-full" />
        ) : (
          <div className="h-full flex items-center justify-center text-tx-3">
            차트 데이터가 없습니다
          </div>
        )}
      </div>
    </div>
  );
};

export default TradingChartSection;
