package com.muscat.user.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.user.common.enums.response.UserResponse;
import com.muscat.user.common.enums.type.UserRole;
import com.muscat.user.common.exception.AuthenticationException;
import com.muscat.user.common.exception.UserException;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@DisplayName("토큰 · Keycloak ID 로 사용자 id 찾기")
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
    given(userRepository.findByKeycloakId("kc-1"))
      .willReturn(Optional.of(User.builder().id(7L).emailVerified(true).build()));

    assertThat(authUtil.requireUserId(jwt(b -> b.claim("email", "old@example.com")))).isEqualTo(7L);
    verify(userRepository, never()).findByEmail(any());
  }

  @Test
  @DisplayName("토큰이 없거나 sub 가 비면 인증 실패, DB 에 그 sub 가 없으면 사용자 없음이다")
  void failures() {
    assertThatThrownBy(() -> authUtil.requireUserId((Jwt) null)).isInstanceOf(AuthenticationException.class);
    assertThatThrownBy(() -> authUtil.requireUserId(jwt(b -> b.subject(" "))))
      .isInstanceOf(AuthenticationException.class);
    assertThatThrownBy(() -> authUtil.requireUserId(jwt(b -> b.claim("email", "user@example.com"))))
      .isInstanceOfSatisfying(UserException.class,
        e -> assertThat(e.getCode()).isEqualTo(UserResponse.USER_NOT_FOUND));
  }

  @Test
  @DisplayName("Keycloak ID 로 사용자 id 를 찾고, 비활성이면 ACCOUNT_DISABLED · 없으면 USER_NOT_FOUND")
  void byKeycloakId() {
    User disabled = User.builder().id(8L).emailVerified(true).build();
    disabled.changeEnabledStatus(false);
    given(userRepository.findByKeycloakId("kc-1"))
      .willReturn(Optional.of(User.builder().id(7L).emailVerified(true).build()));
    given(userRepository.findByKeycloakId("kc-2")).willReturn(Optional.of(disabled));

    assertThat(authUtil.requireUserId("kc-1")).isEqualTo(7L);
    assertThatThrownBy(() -> authUtil.requireUserId("kc-2"))
      .isInstanceOfSatisfying(UserException.class,
        e -> assertThat(e.getCode()).isEqualTo(UserResponse.ACCOUNT_DISABLED));
    assertThatThrownBy(() -> authUtil.requireUserId("kc-3"))
      .isInstanceOfSatisfying(UserException.class,
        e -> assertThat(e.getCode()).isEqualTo(UserResponse.USER_NOT_FOUND));
  }

  @Test
  @DisplayName("이메일 인증 전 사용자는 토큰이 있어도 막는다")
  void unverified_Rejected() {
    given(userRepository.findByKeycloakId("kc-1")).willReturn(Optional.of(User.builder().id(7L).build()));

    assertThatThrownBy(() -> authUtil.requireUserId(jwt(b -> b.claim("email_verified", true))))
      .isInstanceOfSatisfying(UserException.class,
        e -> assertThat(e.getCode()).isEqualTo(UserResponse.EMAIL_NOT_VERIFIED));
  }

  @Test
  @DisplayName("비활성 사용자는 토큰이 살아 있어도 막는다")
  void disabled_Rejected() {
    User disabled = User.builder().id(7L).emailVerified(true).build();
    disabled.changeEnabledStatus(false);
    given(userRepository.findByKeycloakId("kc-1")).willReturn(Optional.of(disabled));

    assertThatThrownBy(() -> authUtil.requireUserId(jwt(b -> b.claim("email", "user@example.com"))))
      .isInstanceOfSatisfying(UserException.class,
        e -> assertThat(e.getCode()).isEqualTo(UserResponse.ACCOUNT_DISABLED));
  }

  @Test
  @DisplayName("관리자 확인은 DB 역할로 하고, USER 면 ADMIN_ROLE_REQUIRED · 비활성 관리자면 ACCOUNT_DISABLED")
  void requireAdminId_ByDbRole() {
    User disabledAdmin = User.builder().id(9L).role(UserRole.ADMIN).emailVerified(true).build();
    disabledAdmin.changeEnabledStatus(false);
    given(userRepository.findByKeycloakId("kc-1"))
      .willReturn(Optional.of(User.builder().id(7L).role(UserRole.ADMIN).emailVerified(true).build()));
    given(userRepository.findByKeycloakId("kc-2"))
      .willReturn(Optional.of(User.builder().id(8L).role(UserRole.USER).emailVerified(true).build()));
    given(userRepository.findByKeycloakId("kc-3")).willReturn(Optional.of(disabledAdmin));

    assertThat(authUtil.requireAdminId(jwt(b -> { }))).isEqualTo(7L);
    assertThatThrownBy(() -> authUtil.requireAdminId(jwt(b -> b.subject("kc-2"))))
      .isInstanceOfSatisfying(UserException.class,
        e -> assertThat(e.getCode()).isEqualTo(UserResponse.ADMIN_ROLE_REQUIRED));
    assertThatThrownBy(() -> authUtil.requireAdminId(jwt(b -> b.subject("kc-3"))))
      .isInstanceOfSatisfying(UserException.class,
        e -> assertThat(e.getCode()).isEqualTo(UserResponse.ACCOUNT_DISABLED));
    assertThatThrownBy(() -> authUtil.requireAdminId(null)).isInstanceOf(AuthenticationException.class);
  }

  @Test
  @DisplayName("다른 서비스가 묻는 관리자 여부는 활성 · 이메일 인증 · DB 역할 ADMIN 일 때만 참이고 예외를 던지지 않는다")
  void isActiveAdmin_OnlyActiveVerifiedAdmin() {
    User disabledAdmin = User.builder().id(9L).role(UserRole.ADMIN).emailVerified(true).build();
    disabledAdmin.changeEnabledStatus(false);
    given(userRepository.findByKeycloakId("kc-1"))
      .willReturn(Optional.of(User.builder().id(7L).role(UserRole.ADMIN).emailVerified(true).build()));
    given(userRepository.findByKeycloakId("kc-2"))
      .willReturn(Optional.of(User.builder().id(8L).role(UserRole.USER).emailVerified(true).build()));
    given(userRepository.findByKeycloakId("kc-3")).willReturn(Optional.of(disabledAdmin));
    given(userRepository.findByKeycloakId("kc-4"))
      .willReturn(Optional.of(User.builder().id(10L).role(UserRole.ADMIN).emailVerified(false).build()));
    given(userRepository.findByKeycloakId("kc-5")).willReturn(Optional.empty());

    assertThat(authUtil.isActiveAdmin("kc-1")).isTrue();
    assertThat(authUtil.isActiveAdmin("kc-2")).isFalse();
    assertThat(authUtil.isActiveAdmin("kc-3")).isFalse();
    assertThat(authUtil.isActiveAdmin("kc-4")).isFalse();
    assertThat(authUtil.isActiveAdmin("kc-5")).isFalse();
  }
}
