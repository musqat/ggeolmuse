export type SupportedSymbol = string;

export interface Stock {
  symbol: string;
  name: string;
  currentPrice?: number;
  change?: number;
  changePercent?: number;
  marketCap?: number;
  volume?: number;
}

export interface StockPrice {
  symbol: string;
  currentPrice: number;
  previousClose: number;
  changePercent: number;
  volume: number;
  marketCap?: number;
  timestamp: string;
}

