package com.muscat.trade.domain.service;

import com.muscat.messaging.event.TradeRejectedEvent;

public interface TradeCancellationService {

  // 잔액에 반영하지 못한 체결을 취소하고 그 종목 보유를 다시 쌓는다
  void cancel(TradeRejectedEvent event);
}
