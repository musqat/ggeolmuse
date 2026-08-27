import { useCallback, useState } from 'react';
import type { ChartPeriod } from '../types';

export const useChartPeriod = (initialPeriod: ChartPeriod = 'purchase') => {
  const [chartPeriod, setChartPeriod] = useState<ChartPeriod>(initialPeriod);
  const [customStartDate, setCustomStartDate] = useState('');
  const [showCustomInput, setShowCustomInput] = useState(false);

  // 훅의 상태를 읽지 않는 순수 함수다. useCallback 으로 참조를 고정해야
  // 이 함수를 쓰는 effect 들이 의존성에 그대로 넣을 수 있다.
  const getStartDateFromPeriod = useCallback((
    period: ChartPeriod,
    originalStartDate: string,
    customDate?: string
  ): string => {
    const today = new Date();

    switch (period) {
      case '1y': {
        const oneYearAgo = new Date(today);
        oneYearAgo.setFullYear(today.getFullYear() - 1);
        return oneYearAgo.toISOString().split('T')[0];
      }
      case '3y': {
        const threeYearsAgo = new Date(today);
        threeYearsAgo.setFullYear(today.getFullYear() - 3);
        return threeYearsAgo.toISOString().split('T')[0];
      }
      case '5y': {
        const fiveYearsAgo = new Date(today);
        fiveYearsAgo.setFullYear(today.getFullYear() - 5);
        return fiveYearsAgo.toISOString().split('T')[0];
      }
      case '10y': {
        const tenYearsAgo = new Date(today);
        tenYearsAgo.setFullYear(today.getFullYear() - 10);
        return tenYearsAgo.toISOString().split('T')[0];
      }
      case '20y': {
        const twentyYearsAgo = new Date(today);
        twentyYearsAgo.setFullYear(today.getFullYear() - 20);
        return twentyYearsAgo.toISOString().split('T')[0];
      }
      case 'custom':
        return customDate || originalStartDate;
      case 'purchase':
      default:
        return originalStartDate;
    }
  }, []);

  const handlePeriodChange = (period: ChartPeriod) => {
    setChartPeriod(period);
    if (period === 'custom') {
      setShowCustomInput(true);
    } else {
      setShowCustomInput(false);
    }
  };

  return {
    chartPeriod,
    customStartDate,
    showCustomInput,
    setChartPeriod: handlePeriodChange,
    setCustomStartDate,
    setShowCustomInput,
    getStartDateFromPeriod,
  };
};
