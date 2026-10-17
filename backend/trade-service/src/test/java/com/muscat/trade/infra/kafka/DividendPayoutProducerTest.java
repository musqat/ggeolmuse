package com.muscat.trade.infra.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.DividendPayoutEvent;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.entity.DividendPayout;
import com.muscat.trade.domain.service.DividendAmounts;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

@DisplayName("배당 몫 이벤트 발행")
class DividendPayoutProducerTest {

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, DividendPayoutEvent> template = mock(KafkaTemplate.class);

  private final DividendPayoutProducer producer = new DividendPayoutProducer(template);

  private static DividendPayout payout() {
    return DividendPayout.of("user-1", 20L, "AAPL", LocalDate.of(2024, 2, 9),
      new DividendAmounts(new BigDecimal("0.24"), new BigDecimal("10"), new BigDecimal("2.40"),
        new BigDecimal("0.37"), new BigDecimal("2.03")),
      LocalDateTime.of(2026, 10, 18, 9, 0));
  }

  @Test
  @DisplayName("사용자 키로 그 배당락일 몫 전체를 싣는다")
  void publish_SendsTargetAmount() {
    given(template.send(anyString(), anyString(), any())).willReturn(KafkaSends.sent("trading.dividend.payout"));

    producer.publish(payout());

    ArgumentCaptor<DividendPayoutEvent> event = ArgumentCaptor.forClass(DividendPayoutEvent.class);
    verify(template).send(eq("trading.dividend.payout"), eq("user-1"), event.capture());
    assertThat(event.getValue().getAccountId()).isEqualTo(20L);
    assertThat(event.getValue().getSymbol()).isEqualTo("AAPL");
    assertThat(event.getValue().getExDate()).isEqualTo("2024-02-09");
    assertThat(event.getValue().getQuantity()).isEqualByComparingTo("10");
    assertThat(event.getValue().getNetAmount()).isEqualByComparingTo("2.03");
    assertThat(event.getValue().getCurrency()).isEqualTo("USD");
  }

  @Test
  @DisplayName("브로커가 받지 못하면 이벤트 발행 실패로 올린다")
  void publish_BrokerDown_EventPublishFailed() {
    given(template.send(anyString(), anyString(), any())).willReturn(KafkaSends.failed());

    Throwable thrown = catchThrowable(() -> producer.publish(payout()));

    assertThat(thrown).isInstanceOf(TradeException.class)
      .hasFieldOrPropertyWithValue("errorCode", "EVENT_PUBLISH_FAILED");
  }

  @Test
  @DisplayName("기다리다 끊기면 끊김 표시를 되살리고 이벤트 발행 실패로 올린다")
  void publish_Interrupted_RestoresFlag() {
    given(template.send(anyString(), anyString(), any())).willReturn(KafkaSends.interrupted());

    Throwable thrown = catchThrowable(() -> producer.publish(payout()));

    assertThat(thrown).isInstanceOf(TradeException.class)
      .hasFieldOrPropertyWithValue("errorCode", "EVENT_PUBLISH_FAILED");
    assertThat(Thread.interrupted()).isTrue();
  }
}
