package com.muscat.user.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.user.common.enums.responses.UserResponse;
import com.muscat.user.common.exceptions.AuthenticationException;
import com.muscat.user.common.exceptions.UserException;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@DisplayName("토큰의 사용자 id 찾기")
class AuthUtilTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final AuthUtil authUtil = new AuthUtil(userRepository, mock(JwtDecoder.class));

  private static Jwt jwt(Consumer<Jwt.Builder> claims) {
    Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none").subject("kc-1")
      .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
    claims.accept(builder);
    return builder.build();
  }

  @Test
  @DisplayName("토큰 sub 의 사용자 id 를 돌려주고 email 클레임으로는 찾지 않는다")
  void bySubject() {
    given(userRepository.findByKeycloakId("kc-1")).willReturn(Optional.of(User.builder().id(7L).build()));

    assertThat(authUtil.requireUserId(jwt(b -> b.claim("email", "old@example.com")))).isEqualTo(7L);
    verify(userRepository, never()).findByEmail(any());
  }

  @Test
  @DisplayName("토큰이 없거나 sub 가 비면 인증 실패, DB 에 그 sub 가 없으면 사용자 없음이다")
  void failures() {
    assertThatThrownBy(() -> authUtil.requireUserId(null)).isInstanceOf(AuthenticationException.class);
    assertThatThrownBy(() -> authUtil.requireUserId(jwt(b -> b.subject(" "))))
      .isInstanceOf(AuthenticationException.class);
    assertThatThrownBy(() -> authUtil.requireUserId(jwt(b -> b.claim("email", "user@example.com"))))
      .isInstanceOfSatisfying(UserException.class,
        e -> assertThat(e.getCode()).isEqualTo(UserResponse.USER_NOT_FOUND));
  }

  @Test
  @DisplayName("비활성 사용자는 토큰이 살아 있어도 막는다")
  void disabled_Rejected() {
    User disabled = User.builder().id(7L).build();
    disabled.changeEnabledStatus(false);
    given(userRepository.findByKeycloakId("kc-1")).willReturn(Optional.of(disabled));

    assertThatThrownBy(() -> authUtil.requireUserId(jwt(b -> b.claim("email", "user@example.com"))))
      .isInstanceOfSatisfying(UserException.class,
        e -> assertThat(e.getCode()).isEqualTo(UserResponse.ACCOUNT_DISABLED));
  }
}
