import { useCallback, useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { stockApi } from '../services/api';
import { convertOHLCToCandlestick, type CandlestickChartData } from '../types/ohlc';
import { getDateRangeForTimeframe, getTodayString, subtractDays, type Timeframe } from '../utils/dateUtils';

const NO_CANDLES: CandlestickChartData[] = [];

interface TradingChartOptions {
  symbol: string;
  timeframe: Timeframe;
  customStartDate: string;
  customEndDate: string;
}

// 거래 화면의 일봉 · 거래일 · 그날의 봉(차트 범위 밖이면 가장 가까운 지난 날)
export const useTradingChart = ({ symbol, timeframe, customStartDate, customEndDate }: TradingChartOptions) => {
  const [tradeDate, setTradeDate] = useState(() => subtractDays(getTodayString(), 1));

  const dateRange = useMemo(
    () => getDateRangeForTimeframe(timeframe, customStartDate, customEndDate),
    [timeframe, customStartDate, customEndDate]
  );

  const { data: chartData = NO_CANDLES, isLoading: loading } = useQuery({
    queryKey: ['stock', 'ohlc', symbol, dateRange],
    queryFn: async () => {
      if (!dateRange) return [];

      const response = await stockApi.getOHLCData(symbol, dateRange.startDate, dateRange.endDate);
      return Array.isArray(response.data) ? convertOHLCToCandlestick(response.data) : [];
    },
    enabled: !!symbol && !!dateRange,
    staleTime: 5 * 60 * 1000,
  });

  // 새로 받은 차트의 마지막 거래일로 거래일을 옮김
  useEffect(() => {
    if (chartData.length > 0) {
      setTradeDate(chartData[chartData.length - 1].time);
    }
  }, [chartData]);

  const latestOHLC = useMemo(() => {
    if (chartData.length === 0) return null;

    const { open, high, low, close } = chartData[chartData.length - 1];
    return { open, high, low, close };
  }, [chartData]);

  // 그 날짜이거나 그 전 가장 가까운 거래일의 봉
  const findClosestPastDate = useCallback((targetDate: string): CandlestickChartData | null => {
    const target = new Date(targetDate);
    const pastDates = chartData.filter((d) => new Date(d.time) <= target);
    return pastDates.length > 0 ? pastDates[pastDates.length - 1] : null;
  }, [chartData]);

  // 시세가 없으면 null(이전 종목 봉을 남기지 않음)
  const selectedDateOHLC = useMemo(
    () => (tradeDate ? chartData.find((d) => d.time === tradeDate) ?? findClosestPastDate(tradeDate) : null),
    [tradeDate, chartData, findClosestPastDate]
  );

  return { chartData, loading, latestOHLC, tradeDate, setTradeDate, selectedDateOHLC, findClosestPastDate };
};
