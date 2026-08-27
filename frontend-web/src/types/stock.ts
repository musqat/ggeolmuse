export type SupportedSymbol = string;

export interface StockPrice {
  symbol: string;
  name?: string;
  currentPrice: number;
  previousClose: number;
  changePercent: number;
  volume: number;
  marketCap?: number;
  timestamp: string;
}

