package com.muscat.user.infra.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.willThrow;

import com.muscat.messaging.event.DividendReceivedEvent;
import com.muscat.user.domain.account.service.AccountService;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

@DisplayName("배당 입금 소비")
class DividendReceivedEventConsumerTest {

  private final AccountService accountService = mock(AccountService.class);
  private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
  private final DividendReceivedEventConsumer consumer = new DividendReceivedEventConsumer(accountService);

  private final DividendReceivedEvent event = DividendReceivedEvent.builder()
    .userId("7").accountId(3L).symbol("AAPL").exDate("2024-09-16").totalAmount(new BigDecimal("2.75")).build();

  @Test
  @DisplayName("입금을 마친 뒤에만 커밋한다")
  void success_DepositsThenAcknowledges() {
    consumer.handleDividendReceived(event, 0, 10L, acknowledgment);

    verify(accountService).processDividendReceivedEvent(event);
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("입금이 실패하면 커밋하지 않고 예외를 올려 다시 받게 한다")
  void failure_DoesNotAcknowledge() {
    willThrow(new IllegalStateException("계좌 없음")).given(accountService).processDividendReceivedEvent(event);

    assertThatThrownBy(() -> consumer.handleDividendReceived(event, 0, 10L, acknowledgment))
      .isInstanceOf(IllegalStateException.class);
    verify(acknowledgment, never()).acknowledge();
  }
}
