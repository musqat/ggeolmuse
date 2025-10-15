import React from 'react';
import type { ChartPeriod } from '../types';
import { CHART_PERIOD_OPTIONS } from '../constants';

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
  return (
    <div className="flex items-center gap-2 mb-4">
      <span className="text-sm font-medium">기간:</span>
      <div className="flex gap-2">
        {CHART_PERIOD_OPTIONS.map((option) => (
          <button
            key={option.value}
            onClick={() => onPeriodChange(option.value)}
            className={`px-3 py-1 text-sm rounded ${
              chartPeriod === option.value
                ? 'bg-blue-600 text-white'
                : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
            }`}
          >
            {option.label}
          </button>
        ))}
      </div>
      {showCustomInput && (
        <input
          type="date"
          value={customStartDate}
          onChange={(e) => onCustomDateChange(e.target.value)}
          max={new Date().toISOString().split('T')[0]}
          className="px-2 py-1 border border-gray-300 rounded text-sm"
        />
      )}
    </div>
  );
};
