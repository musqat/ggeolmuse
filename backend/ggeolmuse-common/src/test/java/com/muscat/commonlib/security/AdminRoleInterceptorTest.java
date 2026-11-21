package com.muscat.commonlib.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.commonlib.exception.BusinessException;
import com.muscat.commonlib.exception.CommonResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@DisplayName("관리자 역할 확인 인터셉터")
class AdminRoleInterceptorTest {

  private final List<String> asked = new ArrayList<>();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  // admin 권한이 실린 토큰으로 로그인한 상태
  private static void loginWithAdminToken(String subject) {
    Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none")
      .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
    if (subject != null) {
      builder.subject(subject);
    } else {
      builder.claim("email", "admin@example.com");
    }
    SecurityContextHolder.getContext()
      .setAuthentication(new JwtAuthenticationToken(builder.build(), List.of(new SimpleGrantedAuthority("admin"))));
  }

  private boolean preHandle(AdminRoleLookup lookup) {
    AdminRoleLookup recording = keycloakId -> {
      asked.add(keycloakId);
      return lookup.isActiveAdmin(keycloakId);
    };
    return new AdminRoleInterceptor(recording)
      .preHandle(new MockHttpServletRequest("POST", "/api/admin/x"), new MockHttpServletResponse(), new Object());
  }

  @Test
  @DisplayName("토큰에 admin 권한이 남아 있어도 user-service 가 관리자가 아니라고 하면 ADMIN_ROLE_REQUIRED")
  void demotedAdmin_Rejected() {
    loginWithAdminToken("kc-1");

    assertThatThrownBy(() -> preHandle(keycloakId -> false)).isInstanceOfSatisfying(BusinessException.class,
      e -> assertThat(e.getCode()).isEqualTo(CommonResponse.ADMIN_ROLE_REQUIRED));
    assertThat(asked).containsExactly("kc-1");
  }

  @Test
  @DisplayName("user-service 가 활성 관리자라고 하면 토큰 sub 로 물은 뒤 들인다")
  void activeAdmin_Allowed() {
    loginWithAdminToken("kc-1");

    assertThat(preHandle(keycloakId -> true)).isTrue();
    assertThat(asked).containsExactly("kc-1");
  }

  @Test
  @DisplayName("역할을 묻지 못하면 들이지 않고 ADMIN_ROLE_UNAVAILABLE(503), 원인 예외를 싣는다")
  void lookupFailure_Unavailable() {
    loginWithAdminToken("kc-1");
    IllegalStateException down = new IllegalStateException("user-service down");

    assertThatThrownBy(() -> preHandle(keycloakId -> {
      throw down;
    })).isInstanceOfSatisfying(BusinessException.class, e -> {
      assertThat(e.getCode()).isEqualTo(CommonResponse.ADMIN_ROLE_UNAVAILABLE);
      assertThat(e.getCause()).isSameAs(down);
    });
  }

  @Test
  @DisplayName("토큰에 sub 가 없으면 묻지 않고 ADMIN_ROLE_REQUIRED")
  void missingSubject_Rejected() {
    loginWithAdminToken(null);

    assertThatThrownBy(() -> preHandle(keycloakId -> true)).isInstanceOfSatisfying(BusinessException.class,
      e -> assertThat(e.getCode()).isEqualTo(CommonResponse.ADMIN_ROLE_REQUIRED));
    assertThat(asked).isEmpty();
  }
}
