import { useCallback, useState } from 'react';
import type { ChartPeriod } from '../types';
import { getTodayString, subtractMonths } from '../../../../utils/dateUtils';

// 기간별로 오늘에서 뺄 개월 수
const PERIOD_MONTHS: Partial<Record<ChartPeriod, number>> = {
  '1y': 12,
  '3y': 36,
  '5y': 60,
  '10y': 120,
  '20y': 240,
};

export const useChartPeriod = (initialPeriod: ChartPeriod = 'purchase') => {
  const [chartPeriod, setChartPeriod] = useState<ChartPeriod>(initialPeriod);
  const [customStartDate, setCustomStartDate] = useState('');

  // 훅의 상태를 읽지 않는 순수 함수다. useCallback 으로 참조를 고정해야
  // 이 함수를 쓰는 effect 들이 의존성에 그대로 넣을 수 있다.
  const getStartDateFromPeriod = useCallback((
    period: ChartPeriod,
    originalStartDate: string,
    customDate?: string
  ): string => {
    const months = PERIOD_MONTHS[period];
    if (months) return subtractMonths(getTodayString(), months);
    if (period === 'custom') return customDate || originalStartDate;
    return originalStartDate;
  }, []);

  return {
    chartPeriod,
    customStartDate,
    setChartPeriod,
    setCustomStartDate,
    getStartDateFromPeriod,
  };
};
