package com.muscat.user.infra.warmup;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.muscat.user.domain.user.service.KeycloakService;
import com.muscat.user.infra.kafka.LoginEventProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("기동 직후 웜업")
class StartupWarmupTest {

  private final KeycloakService keycloakService = mock(KeycloakService.class);
  private final LoginEventProducer loginEventProducer = mock(LoginEventProducer.class);
  private final StartupWarmup warmup = new StartupWarmup(keycloakService, loginEventProducer);

  @Test
  @DisplayName("Keycloak 토큰 경로와 로그인 이벤트 토픽을 웜업한다")
  void warmsBoth() {
    warmup.warmUp();

    verify(keycloakService).warmUp();
    verify(loginEventProducer).warmUp();
  }

  @Test
  @DisplayName("하나가 실패해도 나머지를 웜업하고 예외를 밖으로 내지 않는다")
  void failureIsLoggedOnly() {
    willThrow(new IllegalStateException("Keycloak 아직 안 뜸")).given(keycloakService).warmUp();

    assertThatCode(warmup::warmUp).doesNotThrowAnyException();
    verify(loginEventProducer).warmUp();
  }
}
