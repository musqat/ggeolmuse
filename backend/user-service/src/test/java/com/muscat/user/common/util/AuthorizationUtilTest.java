package com.muscat.user.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.muscat.user.common.enums.responses.UserResponse;
import com.muscat.user.common.enums.type.UserRole;
import com.muscat.user.common.exceptions.UserException;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@DisplayName("관리자 · 본인 확인")
class AuthorizationUtilTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final AuthorizationUtil authorizationUtil = new AuthorizationUtil(userRepository);

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private static void signIn(String keycloakId) {
    Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(keycloakId)
      .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
  }

  private void inDb(String keycloakId, long id, UserRole role) {
    given(userRepository.findByKeycloakId(keycloakId))
      .willReturn(Optional.of(User.builder().id(id).keycloakId(keycloakId).role(role).build()));
  }

  private static void assertResponse(Throwable thrown, UserResponse expected) {
    assertThat(thrown).isInstanceOf(UserException.class);
    assertThat(((UserException) thrown).getCode()).isEqualTo(expected);
  }

  @Test
  @DisplayName("인증이 없거나 JWT 가 아니면 사용자를 찾지 않고 막는다")
  void notJwt_Rejected() {
    assertResponse(catchThrowable(authorizationUtil::getCurrentKeycloakId), UserResponse.USER_NOT_FOUND);

    SecurityContextHolder.getContext().setAuthentication(
      new UsernamePasswordAuthenticationToken("user", "pw", List.of()));
    assertResponse(catchThrowable(authorizationUtil::getCurrentKeycloakId), UserResponse.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("토큰의 subject 로 DB 사용자를 찾고, 없으면 막는다")
  void currentUser_BySubject() {
    signIn("kc-1");
    inDb("kc-1", 7L, UserRole.USER);

    assertThat(authorizationUtil.getCurrentUser().getId()).isEqualTo(7L);

    signIn("kc-unknown");
    assertResponse(catchThrowable(authorizationUtil::getCurrentUser), UserResponse.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("관리자만 통과하고 일반 사용자는 FORBIDDEN 이다")
  void requireAdmin() {
    signIn("kc-admin");
    inDb("kc-admin", 1L, UserRole.ADMIN);
    assertThatCode(authorizationUtil::requireAdmin).doesNotThrowAnyException();
    assertThat(authorizationUtil.isAdmin()).isTrue();

    signIn("kc-1");
    inDb("kc-1", 7L, UserRole.USER);
    assertResponse(catchThrowable(authorizationUtil::requireAdmin), UserResponse.FORBIDDEN);
    assertThat(authorizationUtil.isAdmin()).isFalse();
  }

  @Test
  @DisplayName("관리자 여부를 확인할 수 없으면(사용자 없음) 관리자가 아니다")
  void isAdmin_UnknownUser_False() {
    signIn("kc-unknown");

    assertThat(authorizationUtil.isAdmin()).isFalse();
  }

  @Test
  @DisplayName("본인이거나 관리자면 통과하고, 남의 것이면 FORBIDDEN 이다")
  void requireSelfOrAdmin() {
    signIn("kc-1");
    inDb("kc-1", 7L, UserRole.USER);
    assertThatCode(() -> authorizationUtil.requireSelfOrAdmin(7L)).doesNotThrowAnyException();
    assertResponse(catchThrowable(() -> authorizationUtil.requireSelfOrAdmin(8L)), UserResponse.FORBIDDEN);

    signIn("kc-admin");
    inDb("kc-admin", 1L, UserRole.ADMIN);
    assertThatCode(() -> authorizationUtil.requireSelfOrAdmin(8L)).doesNotThrowAnyException();
  }
}
