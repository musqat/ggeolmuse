package com.muscat.trade.infra.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.AccountDeletedEvent;
import com.muscat.trade.domain.repository.DividendPayoutRepository;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountDeletedEventConsumer 단위 테스트")
class AccountDeletedEventConsumerTest {

  private static final long ACCOUNT_ID = 7L;

  @Mock
  private TradeRepository tradeRepository;

  @Mock
  private HoldingsRepository holdingsRepository;

  @Mock
  private DividendPayoutRepository dividendPayoutRepository;

  @Mock
  private Acknowledgment acknowledgment;

  @InjectMocks
  private AccountDeletedEventConsumer consumer;

  private static AccountDeletedEvent event() {
    return AccountDeletedEvent.builder().userId("1").accountId(ACCOUNT_ID).accountNumber("100-0007").deletionReason("회원 탈퇴").build();
  }

  @Test
  @DisplayName("계좌의 거래 · 보유 · 배당을 지우고 커밋한다")
  void handle_DeletesTradesHoldingsDividends() {
    consumer.handleAccountDeleted(event(), 0, 0L, acknowledgment);

    verify(tradeRepository).deleteByAccountId(ACCOUNT_ID);
    verify(holdingsRepository).deleteByAccountId(ACCOUNT_ID);
    verify(dividendPayoutRepository).deleteByAccountId(ACCOUNT_ID);
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("지우다 실패하면 커밋하지 않고 다시 던져 Kafka 가 재시도한다")
  void handle_Failure_DoesNotAcknowledge() {
    willThrow(new IllegalStateException("db down")).given(dividendPayoutRepository).deleteByAccountId(ACCOUNT_ID);

    assertThatThrownBy(() -> consumer.handleAccountDeleted(event(), 0, 0L, acknowledgment))
      .isInstanceOf(IllegalStateException.class);
    verify(acknowledgment, never()).acknowledge();
  }
}
