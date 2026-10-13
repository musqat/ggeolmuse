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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.muscat.marketdata.domain.entity.Dividend;
import com.muscat.messaging.event.DividendUpdatedEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

// trade-service 가 이 토픽을 읽어 보유자에게 배당을 준다. 토픽 · 키 · 필드가 바뀌면 배당 지급이 끊긴다
@DisplayName("배당 갱신 이벤트 발행")
class DividendEventProducerTest {

  private static final String TOPIC = "market.dividend.updated";

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, DividendUpdatedEvent> template = mock(KafkaTemplate.class);

  private final DividendEventProducer producer = new DividendEventProducer(template);

  private static Dividend dividend(String symbol) {
    return Dividend.builder().symbol(symbol).exDate(LocalDate.of(2024, 8, 12)).amount(new BigDecimal("0.25"))
      .currency("USD").build();
  }

  @Test
  @DisplayName("종목 코드를 키로 배당락일 · 주당 배당 · 통화를 보낸다")
  void publishesDividend() {
    given(template.send(anyString(), anyString(), any())).willReturn(sent(TOPIC));

    producer.publishDividendUpdated(dividend("AAPL"));

    ArgumentCaptor<DividendUpdatedEvent> sent = ArgumentCaptor.forClass(DividendUpdatedEvent.class);
    verify(template).send(eq(TOPIC), eq("AAPL"), sent.capture());
    DividendUpdatedEvent event = sent.getValue();
    assertThat(event.getEventType()).isEqualTo("DIVIDEND_UPDATED");
    assertThat(event.getSource()).isEqualTo("market-data-service");
    assertThat(event.getSymbol()).isEqualTo("AAPL");
    assertThat(event.getExDate()).isEqualTo(LocalDate.of(2024, 8, 12));
    assertThat(event.getAmount()).isEqualByComparingTo("0.25");
    assertThat(event.getCurrency()).isEqualTo("USD");
  }

  @Test
  @DisplayName("일괄 발행은 배당마다 한 건씩 보내고, 비었거나 없으면 보내지 않는다")
  void publishesBatch() {
    given(template.send(anyString(), anyString(), any())).willReturn(sent(TOPIC));

    producer.publishBatch(null);
    producer.publishBatch(List.of());
    verify(template, never()).send(anyString(), anyString(), any());

    producer.publishBatch(List.of(dividend("AAPL"), dividend("MSFT")));
    verify(template, times(2)).send(eq(TOPIC), anyString(), any());
  }

  @Test
  @DisplayName("브로커가 받지 못해도 배당 수집 쪽으로 예외를 올리지 않는다")
  void sendFails_DoesNotThrow() {
    given(template.send(anyString(), anyString(), any())).willReturn(failed());

    assertThatCode(() -> producer.publishDividendUpdated(dividend("AAPL"))).doesNotThrowAnyException();
  }
}
