import type { CandlestickChartData } from '../../../types/ohlc';

export interface PriceSummary {
  last: CandlestickChartData;
  // 전일 종가 대비. 캔들이 하나뿐이면 null
  change: number | null;
  changePercent: number | null;
}

// 마지막 캔들과 그 전 캔들 종가로 등락을 낸다
export function priceSummary(candles: CandlestickChartData[]): PriceSummary | null {
  if (candles.length === 0) return null;

  const last = candles[candles.length - 1];
  const prevClose = candles.length > 1 ? candles[candles.length - 2].close : 0;
  if (!prevClose) return { last, change: null, changePercent: null };

  const change = last.close - prevClose;
  return { last, change, changePercent: (change / prevClose) * 100 };
}
