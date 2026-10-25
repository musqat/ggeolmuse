package com.muscat.trade.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.Trade;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DisplayName("거래 지표")
class TradeMetricsTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final TradeMetrics metrics = new TradeMetrics(registry);

  private static Trade trade(TradeType type, String totalAmount) {
    return Trade.builder().tradeType(type).totalAmount(new BigDecimal(totalAmount)).build();
  }

  private DistributionSummary summary(String side) {
    return registry.get("trade.completed").tag("side", side).summary();
  }

  @Test
  @DisplayName("완료된 체결을 매수 · 매도로 나눠 건수와 금액(USD)을 쌓는다")
  void recordsCountAndAmountBySide() {
    metrics.recordCompleted(trade(TradeType.BUY, "1501.50"));
    metrics.recordCompleted(trade(TradeType.BUY, "98.50"));
    metrics.recordCompleted(trade(TradeType.SELL, "1498.50"));

    assertThat(summary("buy").count()).isEqualTo(2);
    assertThat(summary("buy").totalAmount()).isEqualTo(1600.0);
    assertThat(summary("sell").count()).isEqualTo(1);
    assertThat(summary("sell").totalAmount()).isEqualTo(1498.5);
  }

  @Test
  @DisplayName("트랜잭션 안에서 완료된 체결은 커밋된 뒤에만 센다")
  void recordsAfterCommit() {
    TransactionSynchronizationManager.initSynchronization();
    try {
      metrics.recordCompleted(trade(TradeType.BUY, "1501.50"));

      assertThat(registry.find("trade.completed").summary()).isNull();

      TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

      assertThat(summary("buy").count()).isEqualTo(1);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("롤백된 트랜잭션의 체결은 세지 않는다")
  void skipsRolledBack() {
    TransactionSynchronizationManager.initSynchronization();
    try {
      metrics.recordCompleted(trade(TradeType.BUY, "1501.50"));

      TransactionSynchronizationManager.getSynchronizations()
        .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

      assertThat(registry.find("trade.completed").summary()).isNull();
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }
}
