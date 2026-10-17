package com.muscat.trade.domain.event;

// 반영 실패로 체결을 취소하고 보유를 다시 쌓은 뒤 알림
public record TradeCancelledEvent(String userId, Long accountId, String symbol) {
}
