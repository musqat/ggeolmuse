package com.muscat.trade.infra.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.TradeRejectedEvent;
import com.muscat.trade.domain.service.TradeCancellationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
@DisplayName("반영 실패 이벤트 소비")
class TradeRejectedEventConsumerTest {

  @Mock
  private TradeCancellationService tradeCancellationService;

  @Mock
  private Acknowledgment acknowledgment;

  @InjectMocks
  private TradeRejectedEventConsumer consumer;

  private final TradeRejectedEvent event =
    TradeRejectedEvent.builder().tradeId(7L).userId("user-1").reasonCode("INSUFFICIENT_USD_BALANCE").build();

  @Test
  @DisplayName("체결을 취소하고 오프셋을 커밋한다")
  void handleTradeRejected_CancelsAndAcks() {
    consumer.handleTradeRejected(event, 0, 3L, acknowledgment);

    verify(tradeCancellationService).cancel(event);
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("취소가 실패하면 커밋하지 않고 예외를 던진다")
  void handleTradeRejected_CancelFails_NoAck() {
    willThrow(new IllegalStateException("db down")).given(tradeCancellationService).cancel(any());

    assertThatThrownBy(() -> consumer.handleTradeRejected(event, 0, 3L, acknowledgment))
      .isInstanceOf(IllegalStateException.class);
    verify(acknowledgment, never()).acknowledge();
  }
}
