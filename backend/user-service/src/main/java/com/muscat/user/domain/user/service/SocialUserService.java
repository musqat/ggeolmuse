package com.muscat.user.domain.user.service;

import com.muscat.user.common.enums.type.AuthType;
import com.muscat.user.domain.user.entity.User;
import java.util.Map;

public interface SocialUserService {

  // Google 사용자 정보를 DB와 동기화
  User syncGoogleUser(Map<String, Object> tokenClaims);

  // Authorization Code를 사용하여 Google 사용자 정보 조회 및 동기화
  User processGoogleLogin(String authorizationCode);

  // 제공자별 중복되지 않는 닉네임 생성
  String generateUniqueNickname(AuthType provider);
}