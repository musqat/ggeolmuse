import { stockApi } from '../../../../services/api';

// 한 종목 가격. 응답에 다른 종목 줄이 섞여 와도 그 종목만 남긴다
export async function fetchSymbolPrices<T extends { symbol: string }>(
  symbol: string,
  startDate: string,
  endDate: string
): Promise<T[]> {
  const response = await stockApi.getOHLCData(symbol, startDate, endDate);
  return Array.isArray(response.data)
    ? (response.data as unknown as T[]).filter((row) => row.symbol === symbol)
    : [];
}
