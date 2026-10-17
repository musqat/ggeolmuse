package com.muscat.user.infra.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.AccountBalanceUpdatedEvent;
import com.muscat.messaging.event.UserLoginFailedEvent;
import com.muscat.messaging.event.UserLoginSuccessEvent;
import com.muscat.user.common.exceptions.UserException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

// 보안 감시 · 잔액 감사 컨슈머는 로그만 남김
@DisplayName("보안 감시 · 잔액 감사 소비")
class AuditLogConsumersTest {

  private final Acknowledgment acknowledgment = mock(Acknowledgment.class);

  @Test
  @DisplayName("로그인 성공 · 실패를 기록하고 커밋한다")
  void securityMonitoring() {
    SecurityMonitoringConsumer consumer = new SecurityMonitoringConsumer();

    consumer.handleLoginSuccess(UserLoginSuccessEvent.builder().userId("7").email("user@example.com").build(),
      0, 1L, acknowledgment);
    consumer.handleLoginFailed(UserLoginFailedEvent.builder().email("user@example.com")
      .failureReason("INVALID_PASSWORD").build(), 0, 2L, acknowledgment);

    verify(acknowledgment, times(2)).acknowledge();
  }

  @Test
  @DisplayName("잔액 변경을 기록하고 커밋한다")
  void accountAudit() {
    new AccountAuditConsumer().handleBalanceUpdated(AccountBalanceUpdatedEvent.builder().userId("7").accountId(3L)
      .updateType("DEPOSIT_KRW").krwChange(new BigDecimal("50000")).build(), 0, 1L, acknowledgment);

    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("커밋이 실패하면 이벤트 처리 실패로 올려 다시 받게 한다")
  void commitFails_ThrowsProcessingFailed() {
    willThrow(new IllegalStateException("consumer closed")).given(acknowledgment).acknowledge();
    SecurityMonitoringConsumer security = new SecurityMonitoringConsumer();
    AccountAuditConsumer audit = new AccountAuditConsumer();

    List<Throwable> thrown = List.of(
      catchThrowable(() -> security.handleLoginSuccess(UserLoginSuccessEvent.builder().userId("7")
        .email("user@example.com").build(), 0, 1L, acknowledgment)),
      catchThrowable(() -> security.handleLoginFailed(UserLoginFailedEvent.builder().email("user@example.com")
        .failureReason("INVALID_PASSWORD").build(), 0, 2L, acknowledgment)),
      catchThrowable(() -> audit.handleBalanceUpdated(AccountBalanceUpdatedEvent.builder().userId("7")
        .accountId(3L).build(), 0, 3L, acknowledgment)));

    assertThat(thrown).allSatisfy(t -> assertThat(t)
      .isInstanceOf(UserException.class)
      .hasFieldOrPropertyWithValue("errorCode", "EVENT_PROCESSING_FAILED")
      .hasRootCauseMessage("consumer closed"));
  }
}
