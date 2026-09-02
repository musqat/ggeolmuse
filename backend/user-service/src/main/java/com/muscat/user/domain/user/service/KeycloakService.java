package com.muscat.user.domain.user.service;

import com.muscat.user.domain.user.dto.request.ChangePasswordRequestDto;
import java.util.Map;

// Keycloak 인증 서비스 인터페이스
public interface KeycloakService {

  // 이메일/비밀번호로 Keycloak 로그인
  String login(String email, String password);

  // Authorization Code를 Keycloak JWT 토큰으로 교환
  String exchangeCodeForToken(String authorizationCode);

  // JWT 토큰에서 클레임 추출
  Map<String, Object> parseTokenClaims(String jwtToken);

  // Keycloak에 사용자 생성
  String createUser(String email, String password);

  // Keycloak에 관리자 사용자 생성 (admin role 부여)
  String createAdminUser(String email, String password);

  // Keycloak 사용자 비밀번호 변경
  void changePassword(String keycloakId, ChangePasswordRequestDto newPassword);

  // Keycloak 사용자 비밀번호 재설정 (현재 비밀번호 불필요)
  void resetPassword(String keycloakId, String newPassword);

  // Keycloak 사용자 삭제
  void deleteUser(String keycloakId);

  // 이메일로 Keycloak 사용자 조회
  String findUserByEmail(String email);

  // Keycloak 사용자에게 realm role 할당
  void assignRealmRole(String keycloakId, String roleName);

  // Refresh token으로 새 Access token 발급
  String refreshToken(String refreshToken);
}