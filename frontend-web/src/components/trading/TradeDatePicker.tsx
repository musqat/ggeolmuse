import React from 'react';
import type { CandlestickChartData } from '@/types/ohlc';
import { getTodayString, subtractDays, subtractMonths } from '@/utils/dateUtils';
import { DateField } from '../common/DateField';

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
  const latest = chartData.length > 0 ? chartData[chartData.length - 1].time : null;

  // 그날과 같거나 앞선 가장 가까운 거래일로 옮긴다
  const moveToClosest = (target: string) => {
    const closest = onFindClosestPastDate(target);
    if (closest) onTradeDateChange(closest.time);
  };

  // 빠른 선택. 1주전 · 1달전은 마지막 캔들에서 거슬러 간다
  const quickPicks = [
    { label: '최신', pick: (last: string) => onTradeDateChange(last) },
    { label: '1주전', pick: (last: string) => moveToClosest(subtractDays(last, 7)) },
    { label: '1달전', pick: (last: string) => moveToClosest(subtractMonths(last, 1)) },
    { label: '가장오래된', pick: () => onTradeDateChange(chartData[0].time) },
  ];

  return (
    <div className="mb-4">
      <label className="block text-sm font-medium text-tx-1 mb-2">거래일</label>

      {/* Quick Select Buttons */}
      <div className="grid grid-cols-4 gap-2 mb-2">
        {quickPicks.map(({ label, pick }) => (
          <button
            key={label}
            type="button"
            onClick={() => latest && pick(latest)}
            disabled={!latest}
            className="px-2 py-1 text-xs bg-elevated text-tx-1 rounded hover:bg-hover disabled:bg-surface/50 disabled:text-tx-3 disabled:cursor-not-allowed transition-colors"
          >
            {label}
          </button>
        ))}
      </div>

      {/* 미래 날짜는 고를 수 없다 */}
      <DateField
        value={tradeDate}
        onChange={onTradeDateChange}
        max={getTodayString()}
        placeholder="날짜를 선택하세요"
        testId="trade-date"
      />

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
              <p className="font-semibold text-gain">${selectedDateOHLC.high.toFixed(2)}</p>
            </div>
            <div>
              <span className="text-tx-2">저가</span>
              <p className="font-semibold text-loss">${selectedDateOHLC.low.toFixed(2)}</p>
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
