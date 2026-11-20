package com.muscat.backtest.infra.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.backtest.domain.repository.BacktestHistoryRepository;
import com.muscat.messaging.event.UserDeletedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.kafka.support.Acknowledgment;

@DisplayName("사용자 삭제 이벤트 받기")
class UserDeletedEventConsumerTest {

  private final BacktestHistoryRepository repository = mock(BacktestHistoryRepository.class);
  private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
  private final UserDeletedEventConsumer consumer = new UserDeletedEventConsumer(repository);

  private static UserDeletedEvent deleted(String keycloakId) {
    return UserDeletedEvent.builder().keycloakId(keycloakId).build();
  }

  @Test
  @DisplayName("그 사용자의 백테스트 이력을 지우고 커밋한다")
  void deletesHistoryAndAcks() {
    consumer.handleUserDeleted(deleted("kc-1"), acknowledgment);

    verify(repository).deleteByUserId("kc-1");
    verify(acknowledgment).acknowledge();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = " ")
  @DisplayName("Keycloak ID 가 비면 비로그인 이력을 건드리지 않게 지우지 않고 넘긴다")
  void blankId_Skipped(String keycloakId) {
    consumer.handleUserDeleted(deleted(keycloakId), acknowledgment);

    verify(repository, never()).deleteByUserId(any());
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("지우다 실패하면 커밋하지 않고 예외를 내 재시도 뒤 DLT 로 넘긴다")
  void failure_NotAcked() {
    given(repository.deleteByUserId("kc-1")).willThrow(new IllegalStateException("db down"));

    assertThatThrownBy(() -> consumer.handleUserDeleted(deleted("kc-1"), acknowledgment))
      .isInstanceOf(IllegalStateException.class);
    verify(acknowledgment, never()).acknowledge();
  }
}
