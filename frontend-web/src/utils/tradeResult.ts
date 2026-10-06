import type { TradeResult } from '../services/api';

/**
 * 체결 응답으로 주문 완료 알림 문구를 만듭니다
 */
export function formatTradeResult(trade: TradeResult): string {
  const isBuy = trade.tradeType === 'BUY';

  return [
    `${isBuy ? '매수' : '매도'} 주문이 체결되었습니다.`,
    '',
    `종목: ${trade.symbol}`,
    `수량: ${trade.quantity}주`,
    `체결가: $${trade.price.toFixed(2)}`,
    `수수료: $${trade.fee.toFixed(2)}`,
    `${isBuy ? '총액' : '받는 금액'}: $${trade.totalAmount.toFixed(2)}`,
    `거래일: ${trade.tradeDate}`,
  ].join('\n');
}
