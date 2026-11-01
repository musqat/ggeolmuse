package com.muscat.user.common.util;


import com.muscat.user.common.exception.AuthenticationException;
import com.muscat.user.common.exception.UserException;
import com.muscat.user.common.enums.response.UserResponse;
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
    if (jwt == null) {
      throw new AuthenticationException(UserResponse.AUTHENTICATION_FAILED);
    }

    String keycloakId = jwt.getSubject();
    if (keycloakId == null || keycloakId.isBlank()) {
      throw new AuthenticationException(UserResponse.USER_NOT_FOUND);
    }

    User user = userRepository.findByKeycloakId(keycloakId)
        .orElseThrow(() -> new UserException(UserResponse.USER_NOT_FOUND));

    // 이미 발급된 토큰도 여기서 차단(만료 전까지 유효)
    if (!user.isEnabled()) {
      throw new UserException(UserResponse.ACCOUNT_DISABLED);
    }

    return user.getId();
  }

}
