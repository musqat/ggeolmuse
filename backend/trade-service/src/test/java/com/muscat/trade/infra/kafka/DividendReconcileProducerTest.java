package com.muscat.trade.infra.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.event.DividendReconcileRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

@DisplayName("배당 몫 재계산 요청 발행")
class DividendReconcileProducerTest {

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, DividendReconcileRequest> template = mock(KafkaTemplate.class);

  private final DividendReconcileProducer producer = new DividendReconcileProducer(template);

  @Test
  @DisplayName("계좌 ID 를 키로 보내 같은 계좌 요청이 차례로 처리된다")
  void request_KeyedByAccount() {
    given(template.send(anyString(), anyString(), any())).willReturn(KafkaSends.sent("trading.dividend.reconcile"));

    producer.request("user-1", 20L, "AAPL");

    verify(template).send("trading.dividend.reconcile", "20", new DividendReconcileRequest("user-1", 20L, "AAPL"));
  }

  @Test
  @DisplayName("브로커가 받지 못하면 이벤트 발행 실패로 올린다")
  void request_BrokerDown_EventPublishFailed() {
    given(template.send(anyString(), anyString(), any())).willReturn(KafkaSends.failed());

    Throwable thrown = catchThrowable(() -> producer.request("user-1", 20L, "AAPL"));

    assertThat(thrown).isInstanceOf(TradeException.class)
      .hasFieldOrPropertyWithValue("errorCode", "EVENT_PUBLISH_FAILED");
  }
}
