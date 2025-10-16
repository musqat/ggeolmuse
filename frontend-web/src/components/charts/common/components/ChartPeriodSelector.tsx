import React, { useState } from 'react';
import type { ChartPeriod } from '../types';
import { CHART_PERIOD_OPTIONS } from '../constants';
import DatePicker from '../../../common/DatePicker';

interface ChartPeriodSelectorProps {
  chartPeriod: ChartPeriod;
  customStartDate: string;
  showCustomInput: boolean;
  onPeriodChange: (period: ChartPeriod) => void;
  onCustomDateChange: (date: string) => void;
}

export const ChartPeriodSelector: React.FC<ChartPeriodSelectorProps> = ({
  chartPeriod,
  customStartDate,
  showCustomInput,
  onPeriodChange,
  onCustomDateChange,
}) => {
  const [showDatePicker, setShowDatePicker] = useState(false);
  const [startDateObj, setStartDateObj] = useState<Date | null>(
    customStartDate ? new Date(customStartDate) : null
  );

  const handleStartDateChange = (date: Date | null) => {
    setStartDateObj(date);
    if (date) {
      onCustomDateChange(date.toISOString().split('T')[0]);
    }
  };

  return (
    <div className="mb-4">
      <div className="flex items-center gap-2">
        <span className="text-sm font-medium">기간:</span>
        <div className="flex gap-2">
          {CHART_PERIOD_OPTIONS.map((option) => (
            <button
              key={option.value}
              onClick={() => {
                onPeriodChange(option.value);
                setShowDatePicker(false);
              }}
              className={`px-3 py-1 text-sm rounded ${
                chartPeriod === option.value
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
              }`}
            >
              {option.label}
            </button>
          ))}
          <button
            onClick={() => {
              setShowDatePicker(!showDatePicker);
              if (!showDatePicker) {
                onPeriodChange('custom' as ChartPeriod);
              }
            }}
            className={`px-3 py-1 text-sm rounded ${
              showDatePicker
                ? 'bg-indigo-600 text-white'
                : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
            }`}
          >
            사용자 지정
          </button>
        </div>
      </div>

      {showDatePicker && (
        <div className="mt-4">
          <DatePicker
            value={startDateObj}
            onChange={handleStartDateChange}
          />
        </div>
      )}
    </div>
  );
};
