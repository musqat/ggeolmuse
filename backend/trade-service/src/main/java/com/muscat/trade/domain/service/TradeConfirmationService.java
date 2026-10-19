package com.muscat.trade.domain.service;

import com.muscat.messaging.event.TradeSettledEvent;

public interface TradeConfirmationService {

  // 잔액 정산이 끝난 대기 매수를 완료하고 그 종목 보유를 다시 쌓음
  void confirm(TradeSettledEvent event);
}
