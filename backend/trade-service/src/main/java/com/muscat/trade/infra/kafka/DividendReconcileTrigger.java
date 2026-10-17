package com.muscat.trade.infra.kafka;

import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.event.TradeCancelledEvent;
import com.muscat.trade.domain.event.TradeSavedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 체결 · 취소가 커밋되면 그 계좌 · 종목의 배당 몫 재계산을 요청
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendReconcileTrigger {

  private final DividendReconcileProducer dividendReconcileProducer;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTradeSaved(TradeSavedEvent event) {
    Trade trade = event.trade();
    request(trade.getUserId(), trade.getAccountId(), trade.getSymbol());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onTradeCancelled(TradeCancelledEvent event) {
    request(event.userId(), event.accountId(), event.symbol());
  }

  // 못 보낸 요청은 관리자 미리 보기의 어긋남으로 잡히고 백필로 맞춤
  private void request(String userId, Long accountId, String symbol) {
    try {
      dividendReconcileProducer.request(userId, accountId, symbol);
    } catch (RuntimeException e) {
      log.warn("배당 몫 재계산 요청 실패: accountId={}, symbol={}, error={}", accountId, symbol, e.getMessage());
    }
  }
}
