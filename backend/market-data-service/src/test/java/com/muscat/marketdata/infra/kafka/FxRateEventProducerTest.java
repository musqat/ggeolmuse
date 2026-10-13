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

import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.messaging.event.FxRateUpdatedEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

@DisplayName("환율 갱신 이벤트 발행")
class FxRateEventProducerTest {

  private static final String TOPIC = "market.fxrate.updated";

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, FxRateUpdatedEvent> template = mock(KafkaTemplate.class);

  private final FxRateEventProducer producer = new FxRateEventProducer(template);

  private final FxRate fxRate = FxRate.builder().date(LocalDate.of(2024, 8, 12)).rate(new BigDecimal("1365.5")).build();

  private FxRateUpdatedEvent published() {
    ArgumentCaptor<FxRateUpdatedEvent> event = ArgumentCaptor.forClass(FxRateUpdatedEvent.class);
    verify(template).send(eq(TOPIC), eq("2024-08-12"), event.capture());
    return event.getValue();
  }

  @Test
  @DisplayName("날짜를 키로 USD/KRW 환율과 직전 환율을 보낸다")
  void publishesWithPrevious() {
    given(template.send(anyString(), anyString(), any())).willReturn(sent(TOPIC));

    producer.publishFxRateUpdated(fxRate, new BigDecimal("1350"));

    FxRateUpdatedEvent event = published();
    assertThat(event.getEventType()).isEqualTo("FX_RATE_UPDATED");
    assertThat(event.getCurrencyPair()).isEqualTo("USD/KRW");
    assertThat(event.getDate()).isEqualTo(LocalDate.of(2024, 8, 12));
    assertThat(event.getRate()).isEqualByComparingTo("1365.5");
    assertThat(event.getPreviousRate()).isEqualByComparingTo("1350");
  }

  @Test
  @DisplayName("직전 환율 없이 부르면 직전 환율 칸이 빈다")
  void publishesWithoutPrevious() {
    given(template.send(anyString(), anyString(), any())).willReturn(sent(TOPIC));

    producer.publishFxRateUpdated(fxRate);

    assertThat(published().getPreviousRate()).isNull();
  }

  @Test
  @DisplayName("브로커가 받지 못해도 환율 수집 쪽으로 예외를 올리지 않는다")
  void sendFails_DoesNotThrow() {
    given(template.send(anyString(), anyString(), any())).willReturn(failed());

    assertThatCode(() -> producer.publishFxRateUpdated(fxRate)).doesNotThrowAnyException();
  }
}
