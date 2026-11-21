package com.muscat.user.common.util;


import com.muscat.user.common.exception.AuthenticationException;
import com.muscat.user.common.exception.UserException;
import com.muscat.user.common.enums.response.UserResponse;
import com.muscat.user.common.enums.type.UserRole;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

@Component
public class AuthUtil {

  private final UserRepository userRepository;
  private final JwtDecoder jwtDecoder;

  public AuthUtil(UserRepository userRepository, JwtDecoder jwtDecoder) {
    this.userRepository = userRepository;
    this.jwtDecoder = jwtDecoder;
  }

  // 토큰 sub(Keycloak ID)로 찾은 사용자 id
  public Long requireUserId(Jwt jwt) {
    return requireUserId(subject(jwt));
  }

  // 토큰 sub 로 찾은 사용자 가운데 DB 역할이 관리자인 사용자 id
  public Long requireAdminId(Jwt jwt) {
    User user = requireActiveUser(subject(jwt));
    if (user.getRole() != UserRole.ADMIN) {
      throw new UserException(UserResponse.ADMIN_ROLE_REQUIRED);
    }
    return user.getId();
  }

  // Keycloak ID 로 찾은 활성 · 이메일 인증된 사용자 id
  public Long requireUserId(String keycloakId) {
    return requireActiveUser(keycloakId).getId();
  }

  // Keycloak ID 사용자가 활성 · 이메일 인증 · DB 역할 관리자인지(없으면 거짓)
  public boolean isActiveAdmin(String keycloakId) {
    return userRepository.findByKeycloakId(keycloakId)
      .filter(user -> user.isEnabled() && user.isEmailVerified())
      .map(user -> user.getRole() == UserRole.ADMIN)
      .orElse(false);
  }

  private static String subject(Jwt jwt) {
    if (jwt == null) {
      throw new AuthenticationException(UserResponse.AUTHENTICATION_FAILED);
    }
    String keycloakId = jwt.getSubject();
    if (keycloakId == null || keycloakId.isBlank()) {
      throw new AuthenticationException(UserResponse.USER_NOT_FOUND);
    }
    return keycloakId;
  }

  private User requireActiveUser(String keycloakId) {
    User user = userRepository.findByKeycloakId(keycloakId)
      .orElseThrow(() -> new UserException(UserResponse.USER_NOT_FOUND));

    // 이미 발급된 토큰도 여기서 차단(만료 전까지 유효)
    if (!user.isEnabled()) {
      throw new UserException(UserResponse.ACCOUNT_DISABLED);
    }

    // 이메일 인증 전 사용자는 토큰이 있어도 차단
    if (!user.isEmailVerified()) {
      throw new UserException(UserResponse.EMAIL_NOT_VERIFIED);
    }

    return user;
  }

}
