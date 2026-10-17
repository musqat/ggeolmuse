package com.muscat.user.infra.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static com.muscat.user.infra.kafka.KafkaSends.interrupted;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.messaging.event.TradeRejectedEvent;
import com.muscat.user.common.exceptions.UserException;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@DisplayName("반영 실패 발행")
class TradeRejectedEventProducerTest {

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, TradeRejectedEvent> template = mock(KafkaTemplate.class);

  private final TradeRejectedEventProducer producer = new TradeRejectedEventProducer(template);

  private final TradeCompletedEvent trade = TradeCompletedEvent.builder()
    .eventId("evt-1").tradeId(7L).userId("user-1").accountId(20L).build();

  @Test
  @DisplayName("체결 ID · 사유 · 원래 이벤트 ID 를 userId 키로 보낸다")
  void publish_SendsRejection() {
    RecordMetadata metadata =
      new RecordMetadata(new TopicPartition("user.trade.rejected", 0), 0L, 0, 0L, 0, 0);
    given(template.send(anyString(), anyString(), any()))
      .willReturn(CompletableFuture.completedFuture(new SendResult<>(null, metadata)));

    producer.publish(trade, "INSUFFICIENT_USD_BALANCE", "USD 잔액이 부족합니다.");

    ArgumentCaptor<TradeRejectedEvent> sent = ArgumentCaptor.forClass(TradeRejectedEvent.class);
    verify(template).send(eq("user.trade.rejected"), eq("user-1"), sent.capture());
    assertThat(sent.getValue().getTradeId()).isEqualTo(7L);
    assertThat(sent.getValue().getReasonCode()).isEqualTo("INSUFFICIENT_USD_BALANCE");
    assertThat(sent.getValue().getOriginalEventId()).isEqualTo("evt-1");
  }

  @Test
  @DisplayName("브로커가 받지 못하면 이벤트 발행 실패로 올린다")
  void publish_SendFails_Throws() {
    given(template.send(anyString(), anyString(), any()))
      .willReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

    assertThatThrownBy(() -> producer.publish(trade, "INSUFFICIENT_USD_BALANCE", "x"))
      .isInstanceOf(UserException.class)
      .hasFieldOrPropertyWithValue("errorCode", "EVENT_PUBLISH_FAILED")
      .hasRootCauseMessage("broker down");
  }

  @Test
  @DisplayName("기다리는 중에 인터럽트되면 인터럽트 상태를 되살리고 이벤트 발행 실패로 올린다")
  void publish_Interrupted_RestoresInterrupt() {
    given(template.send(anyString(), anyString(), any())).willReturn(interrupted());

    Throwable thrown = catchThrowable(() -> producer.publish(trade, "INSUFFICIENT_USD_BALANCE", "x"));

    assertThat(Thread.interrupted()).isTrue();
    assertThat(thrown)
      .isInstanceOf(UserException.class)
      .hasFieldOrPropertyWithValue("errorCode", "EVENT_PUBLISH_FAILED")
      .hasCauseInstanceOf(InterruptedException.class);
  }
}
