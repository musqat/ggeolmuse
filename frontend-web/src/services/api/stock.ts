import type { ApiResponse } from '../../types/api';
import type { StockPrice } from '../../types/stock';
import type { OHLCData } from '../../types/ohlc';
import { apiClient } from './client';

// 종목 한 건(/market/symbols 는 자동완성용이라 symbol 만)
export interface AssetSummary {
  symbol: string;
  name?: string;
  assetType?: string;
  marketCap?: number;
  currentPrice?: number;
  date?: string;
  available?: boolean;
  dividendYieldPercent?: number | null;
}

// 종목 목록 정렬 기준(시가총액 큰 순 · 최근 1년 배당수익률 높은 순)
export type StockSort = 'marketCap' | 'dividendYield';

export const stockApi = {
  // 전체 종목 목록 조회
  getAllSymbols: () =>
    apiClient.get<AssetSummary[]>(`/market/symbols`),

  // 특정 종목 현재가 조회
  getCurrentPrice: (symbol: string) =>
    apiClient.get<ApiResponse<StockPrice>>(`/market/price/${symbol}`),

  // 여러 종목의 현재가를 한 번에 조회
  getCurrentPrices: (symbols: string[]) =>
    apiClient.get<{ [symbol: string]: StockPrice }>(`/market/prices`, {
      params: { symbols }
    }),

  // 종목 목록 페이지(현재가 · 시총 · 배당수익률)
  getAllStocksWithPrices: (page: number = 0, size: number = 50, assetType?: string, sort: StockSort = 'marketCap') =>
    apiClient.get<{ content: AssetSummary[]; totalPages: number; totalElements: number }>(`/market/stocks`, {
      params: { page, size, assetType, sort }
    }),

  // 특정 종목의 OHLC 데이터 조회
  getOHLCData: (symbol: string, startDate?: string, endDate?: string) =>
    apiClient.get<OHLCData[]>(`/market/ohlc/multiple`, {
      params: { symbols: [symbol], startDate, endDate }
    }),

  // Bulk 환율 조회 (여러 날짜 한 번에) - Public API
  getExchangeRatesBulk: (dates: string[]) =>
    apiClient.post<Record<string, number>>('/market/fx/bulk', dates),
};
