package com.muscat.trade.infra.kafka;

import static com.muscat.trade.infra.kafka.KafkaSends.failed;
import static com.muscat.trade.infra.kafka.KafkaSends.sent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.DividendReceivedEvent;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

// user-service 가 이 토픽을 읽어 계좌에 배당금을 넣는다. 토픽 · 키 · 필드가 바뀌면 입금이 끊긴다
@DisplayName("배당 입금 이벤트 발행")
class DividendEventProducerTest {

  private static final String TOPIC = "trading.dividend.received";

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, DividendReceivedEvent> template = mock(KafkaTemplate.class);

  private final DividendEventProducer producer = new DividendEventProducer(template);

  @Test
  @DisplayName("사용자 id 를 키로 계좌 · 종목 · 배당락일 · 주당 배당 · 수량 · 세후 금액을 USD 로 보낸다")
  void publish_SendsEventKeyedByUser() {
    given(template.send(anyString(), anyString(), any())).willReturn(sent(TOPIC));

    producer.publishDividendReceived("user-1", 20L, "AAPL", "2024-09-16",
      new BigDecimal("0.25"), new BigDecimal("13"), new BigDecimal("2.75"));

    ArgumentCaptor<DividendReceivedEvent> sent = ArgumentCaptor.forClass(DividendReceivedEvent.class);
    verify(template).send(eq(TOPIC), eq("user-1"), sent.capture());
    DividendReceivedEvent event = sent.getValue();
    assertThat(event.getEventType()).isEqualTo("DIVIDEND_RECEIVED");
    assertThat(event.getSource()).isEqualTo("trade-service");
    assertThat(event.getUserId()).isEqualTo("user-1");
    assertThat(event.getAccountId()).isEqualTo(20L);
    assertThat(event.getSymbol()).isEqualTo("AAPL");
    assertThat(event.getExDate()).isEqualTo("2024-09-16");
    assertThat(event.getDividendPerShare()).isEqualByComparingTo("0.25");
    assertThat(event.getQuantity()).isEqualByComparingTo("13");
    assertThat(event.getTotalAmount()).isEqualByComparingTo("2.75");
    assertThat(event.getCurrency()).isEqualTo("USD");
    assertThat(event.getEventId()).isNotBlank();
  }

  @Test
  @DisplayName("브로커가 받지 못하면 예외를 올린다. 지급 루프가 실패를 알아야 다시 받을 수 있다")
  void sendFails_Throws() {
    given(template.send(anyString(), anyString(), any())).willReturn(failed());

    assertThatThrownBy(() -> producer.publishDividendReceived("user-1", 20L, "AAPL", "2024-09-16",
      BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("AAPL");
  }
}
