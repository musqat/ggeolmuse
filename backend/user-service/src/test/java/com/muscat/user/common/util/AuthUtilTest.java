package com.muscat.user.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

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
  @DisplayName("email 클레임의 사용자 id 를 돌려준다")
  void byEmail() {
    given(userRepository.findByEmail("user@example.com")).willReturn(Optional.of(User.builder().id(7L).build()));

    assertThat(authUtil.requireUserId(jwt(b -> b.claim("email", "user@example.com")))).isEqualTo(7L);
  }

  @Test
  @DisplayName("email 이 없으면 preferred_username 으로 찾는다")
  void fallsBackToPreferredUsername() {
    given(userRepository.findByEmail("user@example.com")).willReturn(Optional.of(User.builder().id(7L).build()));

    assertThat(authUtil.requireUserId(jwt(b -> b.claim("preferred_username", "user@example.com")))).isEqualTo(7L);
  }

  @Test
  @DisplayName("토큰이 없거나 이메일을 못 읽으면 인증 실패, DB 에 없으면 사용자 없음이다")
  void failures() {
    assertThatThrownBy(() -> authUtil.requireUserId(null)).isInstanceOf(AuthenticationException.class);
    assertThatThrownBy(() -> authUtil.requireUserId(jwt(b -> b.claim("email", " "))))
      .isInstanceOf(AuthenticationException.class);
    assertThatThrownBy(() -> authUtil.requireUserId(jwt(b -> b.claim("email", "gone@example.com"))))
      .isInstanceOf(UserException.class);
  }
}
