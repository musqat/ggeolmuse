/**
 * 가격 관련 유틸리티 함수
 */

export type PriceType = 'open' | 'high' | 'low' | 'close' | 'limit';

export interface OHLCData {
  open: number;
  high: number;
  low: number;
  close: number;
}

/**
 * 주문 유형에 따른 체결 가격을 계산합니다
 */
export function calculateExecutionPrice(
  priceType: PriceType,
  ohlcData: OHLCData | null,
  limitPrice?: number
): number {
  if (!ohlcData) return 0;

  switch (priceType) {
    case 'open': return ohlcData.open;
    case 'high': return ohlcData.high;
    case 'low': return ohlcData.low;
    case 'close': return ohlcData.close;
    case 'limit': return limitPrice || 0;
    default: return ohlcData.close;
  }
}

/**
 * 가격이 유효한 범위 내에 있는지 검증합니다
 */
export function validatePriceRange(
  price: number,
  ohlcData: OHLCData | null
): { isValid: boolean; message?: string } {
  if (!ohlcData) {
    return {
      isValid: false,
      message: '날짜를 먼저 선택해주세요.',
    };
  }

  const { high, low } = ohlcData;

  if (price < low || price > high) {
    return {
      isValid: false,
      message: `가격이 범위를 벗어났습니다.\n선택 가능 범위: $${low.toFixed(2)} ~ $${high.toFixed(2)}\n입력한 가격: $${price.toFixed(2)}`,
    };
  }

  return { isValid: true };
}

/**
 * 가격을 통화 형식으로 포맷합니다
 */
export function formatPrice(price: number, decimals: number = 2): string {
  return `$${price.toFixed(decimals)}`;
}

/**
 * 총 금액을 계산합니다
 */
export function calculateTotalAmount(price: number, quantity: number): number {
  return price * quantity;
}
