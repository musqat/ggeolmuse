package com.muscat.user.domain.service;

import com.muscat.user.common.enums.type.AuthProvider;
import com.muscat.user.domain.entity.User;
import java.util.Map;

/**
 * 소셜 로그인 사용자 관리 서비스 인터페이스
 */
public interface SocialUserService {

  /**
   * Google 사용자 정보를 DB와 동기화
   * @param tokenClaims Keycloak에서 전달받은 Google 토큰 클레임
   * @return 동기화된 User 엔티티
   */
  User syncGoogleUser(Map<String, Object> tokenClaims);

  /**
   * Authorization Code를 사용하여 Google 사용자 정보 조회 및 동기화
   * @param authorizationCode Keycloak에서 전달받은 인증 코드
   * @return 동기화된 User 엔티티
   */
  User processGoogleLogin(String authorizationCode);

  /**
   * 제공자별 중복되지 않는 닉네임 생성
   * @param provider 인증 제공자 (GOOGLE 등)
   * @return 고유한 닉네임
   */
  String generateUniqueNickname(AuthProvider provider);
}