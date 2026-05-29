import React from 'react';
import { Calendar } from 'lucide-react';

interface DateRangeInputProps {
  startDate: string;
  setStartDate: (date: string) => void;
  endDate: string;
  setEndDate: (date: string) => void;
  startLabel?: string;
  endLabel?: string;
  allowEmptyEnd?: boolean;
  endPlaceholder?: string;
}

export const DateRangeInput: React.FC<DateRangeInputProps> = ({
  startDate,
  setStartDate,
  endDate,
  setEndDate,
  startLabel = '시작일',
  endLabel = '종료일',
  allowEmptyEnd = false,
  endPlaceholder = '현재',
}) => {
  const today = new Date().toISOString().split('T')[0];

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
      {/* 시작일 */}
      <div>
        <label className="flex items-center space-x-2 text-sm font-medium text-tx-1 mb-2">
          <Calendar className="w-4 h-4" />
          <span>{startLabel}</span>
        </label>
        <input
          type="date"
          value={startDate}
          onChange={(e) => setStartDate(e.target.value)}
          max={endDate || today}
          className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
        />
      </div>

      {/* 종료일 */}
      <div>
        <label className="flex items-center space-x-2 text-sm font-medium text-tx-1 mb-2">
          <Calendar className="w-4 h-4" />
          <span>{endLabel}</span>
          {allowEmptyEnd && (
            <span className="text-xs text-tx-3">
              (비워두면 {endPlaceholder})
            </span>
          )}
        </label>
        <input
          type="date"
          value={endDate}
          onChange={(e) => setEndDate(e.target.value)}
          min={startDate}
          max={today}
          placeholder={allowEmptyEnd ? endPlaceholder : undefined}
          className="w-full border border-line-strong rounded-md px-3 py-2 focus:ring-2 focus:ring-brand focus:border-brand"
        />
      </div>
    </div>
  );
};
