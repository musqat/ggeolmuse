package com.muscat.user.infra.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.user.domain.account.service.TradeSettlementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
@DisplayName("체결 이벤트 소비")
class TradeEventConsumerTest {

  @Mock
  private TradeSettlementService tradeSettlementService;

  @Mock
  private Acknowledgment acknowledgment;

  @InjectMocks
  private TradeEventConsumer consumer;

  private final TradeCompletedEvent event =
    TradeCompletedEvent.builder().tradeId(7L).userId("user-1").build();

  @Test
  @DisplayName("정산하고 오프셋을 커밋한다")
  void handleTradeCompleted_SettlesAndAcks() {
    consumer.handleTradeCompleted(event, 0, 3L, acknowledgment);

    verify(tradeSettlementService).settle(event);
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("정산이 실패하면 커밋하지 않고 예외를 던진다")
  void handleTradeCompleted_SettleFails_NoAck() {
    willThrow(new IllegalStateException("db down")).given(tradeSettlementService).settle(any());

    assertThatThrownBy(() -> consumer.handleTradeCompleted(event, 0, 3L, acknowledgment))
      .isInstanceOf(IllegalStateException.class);
    verify(acknowledgment, never()).acknowledge();
  }
}
