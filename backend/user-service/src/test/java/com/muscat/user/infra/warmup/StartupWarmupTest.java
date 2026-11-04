package com.muscat.user.infra.warmup;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.muscat.user.domain.user.service.KeycloakService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("기동 직후 웜업")
class StartupWarmupTest {

  private final KeycloakService keycloakService = mock(KeycloakService.class);
  private final StartupWarmup warmup = new StartupWarmup(keycloakService);

  @Test
  @DisplayName("Keycloak 토큰 경로를 웜업한다")
  void warmsKeycloak() {
    warmup.warmUp();

    verify(keycloakService).warmUp();
  }

  @Test
  @DisplayName("웜업이 실패해도 예외를 밖으로 내지 않는다")
  void failureIsLoggedOnly() {
    willThrow(new IllegalStateException("Keycloak 아직 안 뜸")).given(keycloakService).warmUp();

    assertThatCode(warmup::warmUp).doesNotThrowAnyException();
  }
}
