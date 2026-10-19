package com.muscat.trade.infra.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.TradeSettledEvent;
import com.muscat.trade.domain.service.TradeConfirmationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
@DisplayName("정산됨 이벤트 소비")
class TradeSettledEventConsumerTest {

  @Mock
  private TradeConfirmationService tradeConfirmationService;

  @Mock
  private Acknowledgment acknowledgment;

  @InjectMocks
  private TradeSettledEventConsumer consumer;

  private final TradeSettledEvent event = TradeSettledEvent.builder().tradeId(7L).userId("user-1").build();

  @Test
  @DisplayName("대기 매수 완료를 맡기고 오프셋을 커밋한다")
  void handleTradeSettled_ConfirmsAndAcks() {
    consumer.handleTradeSettled(event, 0, 3L, acknowledgment);

    verify(tradeConfirmationService).confirm(event);
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("완료가 실패하면 커밋하지 않고 예외를 던진다")
  void handleTradeSettled_ConfirmFails_NoAck() {
    willThrow(new IllegalStateException("db down")).given(tradeConfirmationService).confirm(any());

    assertThatThrownBy(() -> consumer.handleTradeSettled(event, 0, 3L, acknowledgment))
      .isInstanceOf(IllegalStateException.class);
    verify(acknowledgment, never()).acknowledge();
  }
}
