package com.muscat.trade.infra.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.muscat.messaging.event.UserDeletedEvent;
import com.muscat.trade.domain.repository.AccountProfitDailyRepository;
import com.muscat.trade.domain.repository.DividendPayoutRepository;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 삭제 이벤트 받기")
class UserDeletedEventConsumerTest {

  @Mock
  private TradeRepository tradeRepository;

  @Mock
  private HoldingsRepository holdingsRepository;

  @Mock
  private DividendPayoutRepository dividendPayoutRepository;

  @Mock
  private AccountProfitDailyRepository accountProfitDailyRepository;

  @Mock
  private Acknowledgment acknowledgment;

  @InjectMocks
  private UserDeletedEventConsumer consumer;

  private static UserDeletedEvent deleted(String keycloakId) {
    return UserDeletedEvent.builder().keycloakId(keycloakId).build();
  }

  @Test
  @DisplayName("그 사용자의 거래 · 보유 · 배당 몫 · 수익 흐름 저장분을 지우고 커밋한다")
  void deletesUserRowsAndAcks() {
    consumer.handleUserDeleted(deleted("kc-1"), acknowledgment);

    verify(tradeRepository).deleteByUserId("kc-1");
    verify(holdingsRepository).deleteByUserId("kc-1");
    verify(dividendPayoutRepository).deleteByUserId("kc-1");
    verify(accountProfitDailyRepository).deleteByUserId("kc-1");
    verify(acknowledgment).acknowledge();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = " ")
  @DisplayName("Keycloak ID 가 비면 지우지 않고 넘긴다")
  void blankId_Skipped(String keycloakId) {
    consumer.handleUserDeleted(deleted(keycloakId), acknowledgment);

    verifyNoInteractions(tradeRepository, holdingsRepository, dividendPayoutRepository, accountProfitDailyRepository);
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("지우다 실패하면 커밋하지 않고 예외를 내 재시도 뒤 DLT 로 넘긴다")
  void failure_NotAcked() {
    willThrow(new IllegalStateException("db down")).given(holdingsRepository).deleteByUserId(any());

    assertThatThrownBy(() -> consumer.handleUserDeleted(deleted("kc-1"), acknowledgment))
      .isInstanceOf(IllegalStateException.class);
    verify(acknowledgment, never()).acknowledge();
  }
}
