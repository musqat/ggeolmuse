export interface OHLCData {
  symbol: string;
  date: string;
  openPrice: number;
  highPrice: number;
  lowPrice: number;
  closePrice: number;
  adjustedClose: number;
  volume: number;
  currency: string;
  available: boolean;
}

export interface CandlestickChartData {
  time: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume?: number;
}

export function convertOHLCToCandlestick(ohlcData: OHLCData[]): CandlestickChartData[] {
  return ohlcData.map(item => ({
    time: item.date,
    open: item.openPrice,
    high: item.highPrice,
    low: item.lowPrice,
    close: item.closePrice,
    volume: item.volume
  }));
}