import type { ChartPeriodOption } from './types';

// 차트 기간 선택 옵션
export const CHART_PERIOD_OPTIONS: ChartPeriodOption[] = [
  { value: 'purchase', label: '매수일부터' },
  { value: '1y', label: '1년' },
  { value: '3y', label: '3년' },
  { value: '5y', label: '5년' },
  { value: '10y', label: '10년' },
  { value: '20y', label: '20년' },
  { value: 'custom', label: '직접설정' },
];

// 차트 색상 팔레트 (다중 종목/전략 비교용)
export const CHART_COLORS = [
  '#3b82f6', // blue
  '#ef4444', // red
  '#10b981', // green
  '#f59e0b', // amber
  '#8b5cf6', // purple
  '#ec4899', // pink
  '#14b8a6', // teal
  '#f97316', // orange
];
