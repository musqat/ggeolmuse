package com.muscat.trade.domain.event;

import com.muscat.trade.domain.entity.Trade;

// 커밋 뒤 trading.trade.completed 로 보낼 체결
public record TradeSavedEvent(Trade trade) {
}
