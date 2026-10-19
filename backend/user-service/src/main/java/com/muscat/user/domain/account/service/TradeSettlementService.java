package com.muscat.user.domain.account.service;

import com.muscat.messaging.event.TradeCompletedEvent;

public interface TradeSettlementService {

  // 체결 이벤트를 잔액에 한 번만 반영하고 결과(정산됨 · 반영 실패)를 알림
  void settle(TradeCompletedEvent event);
}
