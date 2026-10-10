import React from 'react';
import type { ChartPeriod } from '../types';
import { CHART_PERIOD_OPTIONS } from '../constants';
import { DateField } from '../../../common/DateField';
import { getTodayString } from '../../../../utils/dateUtils';

interface ChartPeriodSelectorProps {
  chartPeriod: ChartPeriod;
  customStartDate: string;
  onPeriodChange: (period: ChartPeriod) => void;
  onCustomDateChange: (date: string) => void;
}

// 백테스트 결과 차트의 기간 버튼. 직접설정이면 옆에 시작일 칸을 보인다
export const ChartPeriodSelector: React.FC<ChartPeriodSelectorProps> = ({
  chartPeriod,
  customStartDate,
  onPeriodChange,
  onCustomDateChange,
}) => (
  <div className="mb-4 flex flex-wrap items-center gap-2">
    <span className="text-sm font-medium">기간:</span>
    {CHART_PERIOD_OPTIONS.map((option) => (
      <button
        key={option.value}
        type="button"
        onClick={() => onPeriodChange(option.value)}
        className={`px-3 py-1 text-sm rounded transition-all ${
          chartPeriod === option.value
            ? 'bg-brand text-brand-ink font-semibold'
            : 'bg-elevated/50 text-tx-2 hover:bg-hover hover:text-tx-1 border border-line'
        }`}
      >
        {option.label}
      </button>
    ))}
    {chartPeriod === 'custom' && (
      <div className="w-40">
        <DateField
          value={customStartDate}
          onChange={onCustomDateChange}
          max={getTodayString()}
          placeholder="시작일"
          testId="chart-custom-start"
        />
      </div>
    )}
  </div>
);
