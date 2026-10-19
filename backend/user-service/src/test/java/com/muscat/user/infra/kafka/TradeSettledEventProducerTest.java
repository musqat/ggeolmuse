package com.muscat.user.infra.kafka;

import static com.muscat.user.infra.kafka.KafkaSends.failed;
import static com.muscat.user.infra.kafka.KafkaSends.interrupted;
import static com.muscat.user.infra.kafka.KafkaSends.sent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.messaging.event.TradeSettledEvent;
import com.muscat.user.common.exceptions.UserException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

@DisplayName("정산됨 발행")
class TradeSettledEventProducerTest {

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, TradeSettledEvent> template = mock(KafkaTemplate.class);

  private final TradeSettledEventProducer producer = new TradeSettledEventProducer(template);

  private final TradeCompletedEvent trade = TradeCompletedEvent.builder()
    .eventId("evt-1").tradeId(7L).userId("user-1").accountId(20L).build();

  @Test
  @DisplayName("체결 ID · 계좌 ID · 원래 이벤트 ID 를 userId 키로 보낸다")
  void publish_SendsSettled() {
    given(template.send(anyString(), anyString(), any())).willReturn(sent("user.trade.settled"));

    producer.publish(trade);

    ArgumentCaptor<TradeSettledEvent> event = ArgumentCaptor.forClass(TradeSettledEvent.class);
    verify(template).send(eq("user.trade.settled"), eq("user-1"), event.capture());
    assertThat(event.getValue().getTradeId()).isEqualTo(7L);
    assertThat(event.getValue().getAccountId()).isEqualTo(20L);
    assertThat(event.getValue().getUserId()).isEqualTo("user-1");
    assertThat(event.getValue().getOriginalEventId()).isEqualTo("evt-1");
    assertThat(event.getValue().getEventType()).isEqualTo("TRADE_SETTLED");
  }

  @Test
  @DisplayName("브로커가 받지 못하면 이벤트 발행 실패로 올린다")
  void publish_SendFails_Throws() {
    given(template.send(anyString(), anyString(), any())).willReturn(failed());

    assertThatThrownBy(() -> producer.publish(trade))
      .isInstanceOf(UserException.class)
      .hasFieldOrPropertyWithValue("errorCode", "EVENT_PUBLISH_FAILED")
      .hasRootCauseMessage("broker down");
  }

  @Test
  @DisplayName("기다리는 중에 인터럽트되면 인터럽트 상태를 되살리고 이벤트 발행 실패로 올린다")
  void publish_Interrupted_RestoresInterrupt() {
    given(template.send(anyString(), anyString(), any())).willReturn(interrupted());

    Throwable thrown = catchThrowable(() -> producer.publish(trade));

    assertThat(Thread.interrupted()).isTrue();
    assertThat(thrown)
      .isInstanceOf(UserException.class)
      .hasFieldOrPropertyWithValue("errorCode", "EVENT_PUBLISH_FAILED")
      .hasCauseInstanceOf(InterruptedException.class);
  }
}
