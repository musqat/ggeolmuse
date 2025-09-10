package com.muscat.user.common.util;


import com.muscat.user.common.exceptions.AuthenticationException;
import com.muscat.user.common.exceptions.UserException;
import com.muscat.user.common.enums.responses.UserResponse;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class AuthUtil {

  private final UserRepository userRepository;

  public AuthUtil(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  // JWT에서 직접 Long userId 추출
  public Long requireUserId(Jwt jwt) {
    if (jwt == null) {
      throw new AuthenticationException(UserResponse.AUTHENTICATION_FAILED);
    }

    String email = resolveLoginEmail(jwt);
    if (email == null || email.isBlank()) {
      throw new AuthenticationException(UserResponse.USER_NOT_FOUND);
    }

    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new UserException(UserResponse.USER_NOT_FOUND));

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
