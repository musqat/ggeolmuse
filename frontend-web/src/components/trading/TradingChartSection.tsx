import React from 'react';
import { Calendar } from 'lucide-react';
import CandlestickChart from '@/components/charts/trading/CandlestickChart';
import type { CandlestickChartData } from '@/types/ohlc';
import { getTodayString, type Timeframe } from '@/utils/dateUtils';
import { DateField } from '../common/DateField';

interface TradingChartSectionProps {
  chartData: CandlestickChartData[];
  chartLoading: boolean;
  timeframe: Timeframe;
  onTimeframeChange: (timeframe: Timeframe) => void;
  customStartDate: string;
  customEndDate: string;
  onCustomStartDateChange: (date: string) => void;
  onCustomEndDateChange: (date: string) => void;
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
  const today = getTodayString();
  // 직접설정에서 날짜를 다 고르지 않으면 요청을 안 보낸다
  const customIncomplete = timeframe === '직접설정' && (!customStartDate || !customEndDate);

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
          <div className="w-40">
            <DateField
              value={customStartDate}
              onChange={onCustomStartDateChange}
              max={customEndDate || today}
              placeholder="시작일"
              testId="trade-chart-start"
            />
          </div>
          <span className="text-tx-3">~</span>
          <div className="w-40">
            <DateField
              value={customEndDate}
              onChange={onCustomEndDateChange}
              min={customStartDate}
              max={today}
              placeholder="종료일"
              testId="trade-chart-end"
            />
          </div>
        </div>
      )}

      {/* Chart Display — 높이는 CandlestickChart 가 정한다. 여기서 고정하면 차트가 넘친다.
          로딩 · 안내 칸은 차트와 같은 높이(좁은 화면 380px, 768px 이상 400px) */}
      <div>
        {chartLoading ? (
          <div className="h-[380px] md:h-[400px] flex items-center justify-center">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-brand"></div>
          </div>
        ) : chartData.length > 0 ? (
          <CandlestickChart data={chartData} />
        ) : (
          <div className="h-[380px] md:h-[400px] flex items-center justify-center text-tx-3">
            {customIncomplete ? '시작일과 종료일을 고르세요' : '차트 데이터가 없습니다'}
          </div>
        )}
      </div>
    </div>
  );
};

export default TradingChartSection;
