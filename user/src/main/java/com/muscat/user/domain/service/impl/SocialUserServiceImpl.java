package com.muscat.user.domain.service.impl;

import com.muscat.user.common.enums.type.AuthProvider;
import com.muscat.user.common.exceptions.SocialLoginException;
import com.muscat.user.common.responses.SocialResponse;
import com.muscat.user.domain.entity.User;
import com.muscat.user.domain.mapper.UserMapper;
import com.muscat.user.domain.repository.UserRepository;
import com.muscat.user.domain.service.KeycloakService;
import com.muscat.user.domain.service.SocialUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class SocialUserServiceImpl implements SocialUserService {

  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final KeycloakService keycloakService;

  @Override
  public User syncGoogleUser(Map<String, Object> tokenClaims) {
    String googleId = userMapper.extractGoogleId(tokenClaims);
    String email = (String) tokenClaims.get("email");

    log.info("Google 사용자 동기화 시작 - ID: {}, 이메일: {}", googleId, email);

    // 1. 기존 Google 사용자 확인
    Optional<User> existingGoogleUser = userRepository.findBySocialIdAndProvider(googleId, AuthProvider.GOOGLE);

    if (existingGoogleUser.isPresent()) {
      String nickname = generateUniqueNickname(AuthProvider.GOOGLE);
      log.info("기존 Google 사용자 정보 업데이트: {}", email);
      return userRepository.save(
          userMapper.updateGoogleUser(existingGoogleUser.get(), tokenClaims, nickname)
      );
    }

    // 2. 동일 이메일의 로컬 계정 확인
    Optional<User> existingLocalUser = userRepository.findByEmail(email);
    if (existingLocalUser.isPresent() && existingLocalUser.get().getProvider() == AuthProvider.LOCAL) {
      log.warn("동일 이메일의 로컬 계정 존재: {}", email);
      throw new SocialLoginException(SocialResponse.SOCIAL_EMAIL_CONFLICT);
    }

    // 3. 새 Google 사용자 생성
    String nickname = generateUniqueNickname(AuthProvider.GOOGLE);
    User newUser = userMapper.createGoogleUserFromClaims(tokenClaims, nickname);

    log.info("새 Google 사용자 생성 완료: {}", email);
    return userRepository.save(newUser);
  }

  @Override
  public User processGoogleLogin(String authorizationCode) {
    try {
      log.info("Google 로그인 처리 시작");

      // 1. Authorization Code -> Keycloak JWT
      String keycloakToken = keycloakService.exchangeCodeForToken(authorizationCode);

      // 2. JWT -> UserInfo
      Map<String, Object> userInfo = keycloakService.parseTokenClaims(keycloakToken);

      // 3. DB 동기화
      return syncGoogleUser(userInfo);

    } catch (Exception e) {
      log.error("Google 로그인 처리 실패: {}", e.getMessage());
      throw new SocialLoginException(SocialResponse.GOOGLE_LOGIN_FAILED, "Google 로그인 처리 중 오류가 발생했습니다: " + e.getMessage());
    }
  }

  @Override
  public String generateUniqueNickname(AuthProvider provider) {
    String prefix = switch (provider) {
      case GOOGLE -> "Google";
      default -> "User";
    };

    int randomCode = (int)(Math.random() * 100000);
    String nickname = prefix + randomCode;

    // 중복 체크 및 재생성
    while (userRepository.existsByNickname(nickname)) {
      randomCode = (int)(Math.random() * 100000);
      nickname = prefix + randomCode;
    }

    log.info("고유 닉네임 생성 완료: {}", nickname);
    return nickname;
  }
}