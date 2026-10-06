package com.muscat.trade.infra.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.TradeCancelledEvent;
import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.Trade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

@DisplayName("거래 이벤트 발행")
class TradeEventProducerTest {

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, TradeCompletedEvent> completedTemplate =
    mock(KafkaTemplate.class);
  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, TradeCancelledEvent> cancelledTemplate =
    mock(KafkaTemplate.class);

  private final TradeEventProducer producer =
    new TradeEventProducer(completedTemplate, cancelledTemplate);

  @Test
  @DisplayName("체결 이벤트에 거래한 계좌 ID 를 싣는다")
  void publishTradeCompleted_IncludesAccountId() {
    // given
    given(completedTemplate.send(anyString(), anyString(), any()))
      .willReturn(new CompletableFuture<>());
    Trade trade = Trade.builder()
      .id(7L)
      .userId("user-1")
      .accountId(20L)
      .symbol("TSLL")
      .tradeType(TradeType.BUY)
      .quantity(new BigDecimal("10"))
      .price(new BigDecimal("8.98"))
      .totalAmount(new BigDecimal("90.02"))
      .fee(new BigDecimal("0.22"))
      .tradeDate(LocalDate.of(2026, 10, 5))
      .executedAt(LocalDateTime.of(2026, 10, 6, 7, 12))
      .build();

    // when
    producer.publishTradeCompleted(trade);

    // then
    ArgumentCaptor<TradeCompletedEvent> event = ArgumentCaptor.forClass(TradeCompletedEvent.class);
    verify(completedTemplate).send(eq("trading.trade.completed"), eq("user-1"), event.capture());
    assertThat(event.getValue().getAccountId()).isEqualTo(20L);
  }
}
