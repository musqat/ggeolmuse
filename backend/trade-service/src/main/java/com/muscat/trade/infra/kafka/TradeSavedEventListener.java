package com.muscat.trade.infra.kafka;

import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.event.TradeSavedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 커밋된 체결을 trading.trade.completed 로 발행하고 발행 시각 저장. 실패하면 시각이 빈 채로 남는다
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeSavedEventListener {

  private final TradeEventProducer tradeEventProducer;
  private final TradePublishRecorder tradePublishRecorder;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTradeSaved(TradeSavedEvent event) {
    Trade trade = event.trade();
    try {
      tradeEventProducer.publishTradeCompleted(trade);
      tradePublishRecorder.recordPublished(trade.getId());
    } catch (RuntimeException e) {
      log.warn("체결 이벤트를 바로 보내지 못함: tradeId={}, error={}", trade.getId(), e.getMessage());
    }
  }
}
