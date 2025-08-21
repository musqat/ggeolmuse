package com.muscat.user.domain.mapper;

import com.muscat.user.common.enums.type.AuthProvider;
import com.muscat.user.common.exceptions.SocialLoginException;
import com.muscat.user.common.responses.SocialResponse;
import com.muscat.user.domain.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * User 엔티티 생성을 위한 매퍼 클래스
 */
@Component
@Slf4j
public class UserMapper {

  /**
   * Google 소셜 로그인 사용자 생성
   */
  public User createGoogleUser(String email, String googleId, String nickname, String profileImageUrl) {
    return User.builder()
        .email(email)
        .socialEmail(email)
        .socialId(googleId)
        .nickname(nickname)
        .profileImageUrl(profileImageUrl)
        .provider(AuthProvider.GOOGLE)
        .emailVerified(true) // Google 로그인은 이미 인증됨
        .build();
  }

  /**
   * 로컬 회원가입 사용자 생성
   */
  public User createLocalUser(String email, String password, String nickname, String keycloakId) {
    return User.builder()
        .email(email)
        .password(password)
        .nickname(nickname)
        .keycloakId(keycloakId)
        .provider(AuthProvider.LOCAL)
        .emailVerified(false) // 이메일 인증 필요
        .build();
  }

  /**
   * Google 토큰 클레임에서 사용자 생성
   */
  public User createGoogleUserFromClaims(Map<String, Object> tokenClaims, String nickname) {
    String googleId = extractGoogleId(tokenClaims);
    String email = (String) tokenClaims.get("email");
    String picture = (String) tokenClaims.get("picture");

    log.info("Google 토큰 클레임에서 사용자 생성: {}", email);
    return createGoogleUser(email, googleId, nickname, picture);
  }

  /**
   * 기존 Google 사용자 정보 업데이트
   */
  public User updateGoogleUser(User existingUser, Map<String, Object> tokenClaims, String nickname) {
    String email = (String) tokenClaims.get("email");
    String picture = (String) tokenClaims.get("picture");

    log.info("기존 Google 사용자 정보 업데이트: {}", email);

    // 변경된 정보만 업데이트
    existingUser.setSocialEmail(email);
    existingUser.setProfileImageUrl(picture);

    // 닉네임이 없거나 기존 임시 패턴이면 업데이트
    if (existingUser.getNickname() == null ||
        existingUser.getNickname().startsWith("Google") ||
        existingUser.getNickname().startsWith("GoogleUser")) {
      existingUser.setNickname(nickname);
      log.info("닉네임 업데이트: {}", nickname);
    }

    return existingUser;
  }

  /**
   * Google ID 추출 로직
   */
  public String extractGoogleId(Map<String, Object> tokenClaims) {
    // Keycloak JWT의 sub 클레임이 Google ID가 됨
    String googleId = (String) tokenClaims.get("sub");

    if (googleId == null) {
      // Keycloak broker에서 다른 필드명을 사용할 수 있음
      googleId = (String) tokenClaims.get("preferred_username");
      log.debug("sub 클레임이 없어 preferred_username 사용: {}", googleId);
    }

    if (googleId == null) {
      // 마지막 대안 - 이메일 기반으로 고유 ID 생성
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