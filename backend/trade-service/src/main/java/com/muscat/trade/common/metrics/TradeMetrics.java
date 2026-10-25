package com.muscat.trade.common.metrics;

import com.muscat.trade.domain.entity.Trade;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// 완료된 체결의 건수 · 금액 지표(trade.completed, 태그 매수 · 매도)
@Component
@RequiredArgsConstructor
public class TradeMetrics {

  private static final String NAME = "trade.completed";

  private final MeterRegistry registry;

  // 트랜잭션 안이면 커밋된 뒤에 셈
  public void recordCompleted(Trade trade) {
    String side = trade.getTradeType().name().toLowerCase(Locale.ROOT);
    double amount = trade.getTotalAmount().doubleValue();
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      record(side, amount);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        record(side, amount);
      }
    });
  }

  private void record(String side, double amount) {
    DistributionSummary.builder(NAME)
      .description("완료된 체결의 금액(수수료 포함)")
      .baseUnit("usd")
      .tag("side", side)
      .register(registry)
      .record(amount);
  }
}
