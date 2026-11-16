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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.StockSplitEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DisplayName("분할 이벤트 발행")
class SplitEventProducerTest {

  private static final String TOPIC = "market.split.detected";
  private static final LocalDate SPLIT_DATE = LocalDate.of(2024, 6, 10);

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, StockSplitEvent> template = mock(KafkaTemplate.class);

  private final SplitEventProducer producer = new SplitEventProducer(template);

  @AfterEach
  void tearDown() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("종목 코드를 키로 분할일 · 비율 전송")
  void publishesSplit() {
    given(template.send(anyString(), anyString(), any())).willReturn(sent(TOPIC));

    producer.publishSplitDetected("NVDA", SPLIT_DATE, new BigDecimal("10.00000000"));

    ArgumentCaptor<StockSplitEvent> sent = ArgumentCaptor.forClass(StockSplitEvent.class);
    verify(template).send(eq(TOPIC), eq("NVDA"), sent.capture());
    StockSplitEvent event = sent.getValue();
    assertThat(event.getEventType()).isEqualTo("SPLIT_DETECTED");
    assertThat(event.getSource()).isEqualTo("market-data-service");
    assertThat(event.getSymbol()).isEqualTo("NVDA");
    assertThat(event.getSplitDate()).isEqualTo(SPLIT_DATE);
    assertThat(event.getRatio()).isEqualByComparingTo("10");
  }

  @Test
  @DisplayName("트랜잭션 안에서는 커밋 뒤에 전송")
  void sendsAfterCommit() {
    given(template.send(anyString(), anyString(), any())).willReturn(sent(TOPIC));
    TransactionSynchronizationManager.initSynchronization();

    producer.publishSplitDetected("NVDA", SPLIT_DATE, BigDecimal.TEN);

    verify(template, never()).send(anyString(), anyString(), any());
    List<TransactionSynchronization> registered = TransactionSynchronizationManager.getSynchronizations();
    registered.forEach(TransactionSynchronization::afterCommit);
    verify(template).send(eq(TOPIC), eq("NVDA"), any());
  }

  @Test
  @DisplayName("브로커가 받지 못해도 예외 없음")
  void sendFails_DoesNotThrow() {
    given(template.send(anyString(), anyString(), any())).willReturn(failed());

    assertThatCode(() -> producer.publishSplitDetected("NVDA", SPLIT_DATE, BigDecimal.TEN))
      .doesNotThrowAnyException();
  }
}
