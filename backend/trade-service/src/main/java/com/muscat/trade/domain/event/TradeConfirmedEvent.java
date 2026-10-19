package com.muscat.trade.domain.event;

// 정산된 대기 매수를 완료하고 보유를 다시 쌓은 뒤 알림
public record TradeConfirmedEvent(String userId, Long accountId, String symbol) {
}
