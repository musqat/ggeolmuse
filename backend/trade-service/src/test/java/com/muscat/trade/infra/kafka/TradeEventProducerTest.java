package com.muscat.trade.infra.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

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

  private static Trade trade() {
    return Trade.builder()
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
  }

  private static CompletableFuture<SendResult<String, TradeCompletedEvent>> acked() {
    RecordMetadata metadata =
      new RecordMetadata(new TopicPartition("trading.trade.completed", 0), 0L, 0, 0L, 0, 0);
    return CompletableFuture.completedFuture(new SendResult<>(null, metadata));
  }

  @Test
  @DisplayName("체결 이벤트에 거래한 계좌 ID 를 싣는다")
  void publishTradeCompleted_IncludesAccountId() {
    // given
    given(completedTemplate.send(anyString(), anyString(), any())).willReturn(acked());

    // when
    producer.publishTradeCompleted(trade());

    // then
    ArgumentCaptor<TradeCompletedEvent> event = ArgumentCaptor.forClass(TradeCompletedEvent.class);
    verify(completedTemplate).send(eq("trading.trade.completed"), eq("user-1"), event.capture());
    assertThat(event.getValue().getAccountId()).isEqualTo(20L);
  }

  @Test
  @DisplayName("브로커가 받지 못하면 예외를 낸다")
  void publishTradeCompleted_SendFails_Throws() {
    // given
    given(completedTemplate.send(anyString(), anyString(), any()))
      .willReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

    // when · then
    assertThatThrownBy(() -> producer.publishTradeCompleted(trade()))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("tradeId=7");
  }
}
