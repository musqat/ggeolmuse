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

// 차트가 그리는 주가, 분할만 조정된 종가 · 없으면 수정 종가
export const closeOf = (row: { closePrice?: number; adjustedClose?: number }) =>
  row.closePrice || row.adjustedClose || 0;
