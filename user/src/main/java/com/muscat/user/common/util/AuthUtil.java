package com.muscat.user.common.util;


import com.muscat.user.common.exceptions.AuthenticationException;
import com.muscat.user.common.exceptions.UserException;
import com.muscat.user.common.responses.UserResponse;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class AuthUtil {

  private final UserRepository userRepository;

  public AuthUtil(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  // 현재 SecurityContext에서 Long userId 추출
  public Long requireUserId(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new AuthenticationException(UserResponse.AUTHENTICATION_FAILED, "인증되지 않은 요청입니다.");
    }

    Object principal = authentication.getPrincipal();
    if (!(principal instanceof Jwt jwt)) {
      throw new AuthenticationException(UserResponse.AUTHENTICATION_FAILED, "JWT principal이 아닙니다.");
    }

    String email = resolveLoginEmail(jwt);
    if (email == null || email.isBlank()) {
      throw new AuthenticationException(UserResponse.USER_NOT_FOUND,
          "JWT에서 email/username을 찾을 수 없습니다.");
    }

    User user = userRepository.findByEmail(email)
        .orElseThrow(() ->
            new UserException(UserResponse.USER_NOT_FOUND, "해당 email의 User를 찾을 수 없습니다."));

    return user.getId();
  }

  // JWT에서 email/username 클레임 추출
  private String resolveLoginEmail(Jwt jwt) {
    String email = jwt.getClaimAsString("email");
    if (email == null || email.isBlank()) {
      email = jwt.getClaimAsString("preferred_username"); // 폴백
    }
    return email;
  }

}
