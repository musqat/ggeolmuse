package com.muscat.marketdata.infra.kafka;

import static com.muscat.marketdata.infra.kafka.KafkaSends.failed;
import static com.muscat.marketdata.infra.kafka.KafkaSends.sent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.DataCollectionCompletedEvent;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

// 성공과 실패가 같은 토픽으로 가고 successful · eventType 으로 갈린다
@DisplayName("데이터 수집 결과 이벤트 발행")
class DataCollectionEventProducerTest {

  private static final String TOPIC = "market.collection.completed";
  private static final LocalDate FROM = LocalDate.of(2024, 1, 1);
  private static final LocalDate TO = LocalDate.of(2024, 12, 31);

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, DataCollectionCompletedEvent> template = mock(KafkaTemplate.class);

  private final DataCollectionEventProducer producer = new DataCollectionEventProducer(template);

  private DataCollectionCompletedEvent published() {
    ArgumentCaptor<DataCollectionCompletedEvent> event = ArgumentCaptor.forClass(DataCollectionCompletedEvent.class);
    verify(template).send(eq(TOPIC), eq("AAPL"), event.capture());
    return event.getValue();
  }

  @Test
  @DisplayName("완료는 성공으로 캔들 · 배당 건수와 걸린 시간을 보낸다")
  void completed() {
    given(template.send(anyString(), anyString(), any())).willReturn(sent(TOPIC));

    producer.publishCompleted("AAPL", FROM, TO, 250, 4, 1200L);

    DataCollectionCompletedEvent event = published();
    assertThat(event.getEventType()).isEqualTo("COLLECTION_COMPLETED");
    assertThat(event.isSuccessful()).isTrue();
    assertThat(event.getFromDate()).isEqualTo(FROM);
    assertThat(event.getToDate()).isEqualTo(TO);
    assertThat(event.getCandleCount()).isEqualTo(250);
    assertThat(event.getDividendCount()).isEqualTo(4);
    assertThat(event.getExecutionTimeMs()).isEqualTo(1200L);
    assertThat(event.getErrorMessage()).isNull();
  }

  @Test
  @DisplayName("실패는 건수 0 과 오류 메시지를 보낸다")
  void failedCollection() {
    given(template.send(anyString(), anyString(), any())).willReturn(sent(TOPIC));

    producer.publishFailed("AAPL", FROM, TO, "yahoo 429", 300L);

    DataCollectionCompletedEvent event = published();
    assertThat(event.getEventType()).isEqualTo("COLLECTION_FAILED");
    assertThat(event.isSuccessful()).isFalse();
    assertThat(event.getCandleCount()).isZero();
    assertThat(event.getDividendCount()).isZero();
    assertThat(event.getErrorMessage()).isEqualTo("yahoo 429");
  }

  @Test
  @DisplayName("브로커가 받지 못해도 수집 쪽으로 예외를 올리지 않는다")
  void sendFails_DoesNotThrow() {
    given(template.send(anyString(), anyString(), any())).willReturn(failed());

    assertThatCode(() -> producer.publishCompleted("AAPL", FROM, TO, 1, 0, 1L)).doesNotThrowAnyException();
    assertThatCode(() -> producer.publishFailed("AAPL", FROM, TO, "x", 1L)).doesNotThrowAnyException();
  }
}
