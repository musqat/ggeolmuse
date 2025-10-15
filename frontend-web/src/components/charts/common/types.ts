// 차트 기간 타입 정의
export type ChartPeriod = 'purchase' | '1y' | '3y' | '5y' | '10y' | '20y' | 'custom';

// 차트 기간 옵션 인터페이스
export interface ChartPeriodOption {
  value: ChartPeriod;
  label: string;
}
