package com.muscat.user.common.util;

import com.muscat.user.common.exceptions.SocialLoginException;
import com.muscat.user.common.responses.SocialResponse;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

// Google 소셜 로그인 관련 유틸리티
@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleUserUtil {

  private final UserMapper userMapper;

  // Google 토큰 클레임에서 사용자 생성
  public User createGoogleUserFromClaims(Map<String, Object> tokenClaims, String nickname) {
    String googleId = extractGoogleId(tokenClaims);
    String email = (String) tokenClaims.get("email");
    String picture = (String) tokenClaims.get("picture");

    log.debug("Google 토큰 클레임에서 사용자 생성: {}", email);
    return userMapper.createGoogleUser(email, googleId, nickname, picture);
  }

  // Google ID 추출 로직
  public String extractGoogleId(Map<String, Object> tokenClaims) {
    String googleId = (String) tokenClaims.get("sub");

    if (googleId == null) {
      googleId = (String) tokenClaims.get("preferred_username");
      log.debug("sub 클레임이 없어 preferred_username 사용: {}", googleId);
    }

    if (googleId == null) {
      String email = (String) tokenClaims.get("email");
      if (email != null) {
        googleId = "google_" + email.hashCode();
        log.warn("Google ID를 이메일 해시로 생성: {} -> {}", email, googleId);
      }
    }

    if (googleId == null) {
      log.error("Google ID 추출 실패 - 사용 가능한 클레임: {}", tokenClaims.keySet());
      throw new SocialLoginException(SocialResponse.GOOGLE_USER_INFO_FAILED, "Google ID를 추출할 수 없습니다.");
    }

    log.debug("Google ID 추출 성공: {}", googleId);
    return googleId;
  }
}