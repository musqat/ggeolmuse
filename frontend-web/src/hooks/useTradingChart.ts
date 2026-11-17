import { useCallback, useEffect, useMemo, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
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

// 토 · 일(YYYY-MM-DD 를 UTC 자정으로 봐 시간대와 상관없음)
const isWeekend = (date: string) => [0, 6].includes(new Date(`${date}T00:00:00Z`).getUTCDay());

const closedNotice = (date: string) => `${date} 은 시세가 없는 날(휴장일)이라 고를 수 없습니다`;

// 거래 화면의 일봉 · 거래일 · 거래일의 봉(시세가 있는 날만 거래일로 고름)
export const useTradingChart = ({ symbol, timeframe, customStartDate, customEndDate }: TradingChartOptions) => {
  const queryClient = useQueryClient();
  const [tradeDate, setTradeDate] = useState(() => subtractDays(getTodayString(), 1));
  // 차트 기간 밖에서 고른 날의 봉
  const [outsideCandle, setOutsideCandle] = useState<CandlestickChartData | null>(null);
  const [tradeDateNotice, setTradeDateNotice] = useState<string | null>(null);

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
      setOutsideCandle(null);
      setTradeDateNotice(null);
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

  // 시세가 있는 날만 거래일로(주말 · 봉 없는 날은 그대로 두고 알림, 차트 기간 밖은 그날 봉을 따로 받음)
  const selectTradeDate = useCallback(async (date: string) => {
    if (isWeekend(date)) {
      setTradeDateNotice(closedNotice(date));
      return;
    }
    const inChart = chartData.find((d) => d.time === date);
    if (inChart) {
      setTradeDate(date);
      setOutsideCandle(null);
      setTradeDateNotice(null);
      return;
    }
    const insideRange = !!dateRange && date >= dateRange.startDate && date <= dateRange.endDate;
    if (insideRange) {
      setTradeDateNotice(closedNotice(date));
      return;
    }
    const candles = await queryClient.fetchQuery({
      queryKey: ['stock', 'ohlc', symbol, { startDate: date, endDate: date }],
      queryFn: async () => {
        const response = await stockApi.getOHLCData(symbol, date, date);
        return Array.isArray(response.data) ? convertOHLCToCandlestick(response.data) : [];
      },
      staleTime: 5 * 60 * 1000,
    });
    const candle = candles.find((d) => d.time === date);
    if (!candle) {
      setTradeDateNotice(closedNotice(date));
      return;
    }
    setTradeDate(date);
    setOutsideCandle(candle);
    setTradeDateNotice(null);
  }, [chartData, dateRange, queryClient, symbol]);

  // 고른 거래일의 봉(시세가 없으면 null, 다른 날 봉으로 대신하지 않음)
  const selectedDateOHLC = useMemo(() => {
    if (!tradeDate) return null;
    const inChart = chartData.find((d) => d.time === tradeDate);
    if (inChart) return inChart;
    return outsideCandle?.time === tradeDate ? outsideCandle : null;
  }, [tradeDate, chartData, outsideCandle]);

  return {
    chartData,
    loading,
    latestOHLC,
    tradeDate,
    setTradeDate,
    selectTradeDate,
    tradeDateNotice,
    selectedDateOHLC,
    findClosestPastDate,
  };
};
