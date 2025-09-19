export const SUPPORTED_SYMBOLS = ['AAPL', 'MSFT', 'GOOGL', 'TSLA', 'NVDA'] as const;
export type SupportedSymbol = typeof SUPPORTED_SYMBOLS[number];

export interface Stock {
  symbol: SupportedSymbol;
  name: string;
  currentPrice: number;
  change: number;
  changePercent: number;
  marketCap: string;
  volume: number;
}

export interface StockPrice {
  symbol: string;
  price: number;
  timestamp: string;
}

export interface OHLCData {
  symbol: string;
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}