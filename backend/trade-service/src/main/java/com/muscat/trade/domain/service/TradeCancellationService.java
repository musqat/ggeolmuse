package com.muscat.trade.domain.service;

import com.muscat.messaging.event.TradeRejectedEvent;

public interface TradeCancellationService {

  // 잔액에 반영하지 못한 체결 취소 · 그 종목 보유 다시 쌓기
  void cancel(TradeRejectedEvent event);
}
