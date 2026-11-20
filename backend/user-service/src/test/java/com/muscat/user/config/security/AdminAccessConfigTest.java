package com.muscat.user.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.muscat.user.common.enums.response.UserResponse;
import com.muscat.user.common.enums.type.UserRole;
import com.muscat.user.common.exception.UserException;
import com.muscat.user.common.util.AuthUtil;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@DisplayName("관리자 API 들이기")
class AdminAccessConfigTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final AdminAccessConfig config = new AdminAccessConfig(new AuthUtil(userRepository, mock(JwtDecoder.class)));

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  // admin 권한이 실린 토큰으로 로그인한 상태
  private static void loginWithAdminToken(String subject) {
    Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(subject)
      .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    SecurityContextHolder.getContext()
      .setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("admin"))));
  }

  private static User user(UserRole role) {
    return User.builder().id(7L).role(role).enabled(true).emailVerified(true).build();
  }

  private boolean preHandle() {
    return config.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());
  }

  @Test
  @DisplayName("토큰에 admin 권한이 남아 있어도 DB 역할이 USER 면 막는다")
  void demotedAdmin_Rejected() {
    loginWithAdminToken("kc-1");
    given(userRepository.findByKeycloakId("kc-1")).willReturn(Optional.of(user(UserRole.USER)));

    assertThatThrownBy(this::preHandle).isInstanceOfSatisfying(UserException.class,
      e -> assertThat(e.getCode()).isEqualTo(UserResponse.ADMIN_ROLE_REQUIRED));
  }

  @Test
  @DisplayName("DB 역할이 ADMIN 인 활성 사용자는 들인다")
  void admin_Allowed() {
    loginWithAdminToken("kc-1");
    given(userRepository.findByKeycloakId("kc-1")).willReturn(Optional.of(user(UserRole.ADMIN)));

    assertThat(preHandle()).isTrue();
  }
}
