package com.muscat.user.domain.user.service;

import com.muscat.user.domain.user.dto.response.TokenResponseDto;

// Keycloak 인증 서비스 인터페이스
public interface KeycloakService {

  // 이메일/비밀번호로 Keycloak 로그인. access · refresh token 을 같이 돌려준다
  TokenResponseDto login(String email, String password);

  // 브라우저가 public client 로 시작한 구글 로그인(PKCE)의 인가 코드를 토큰으로 바꾼다
  TokenResponseDto exchangePkceCode(String code, String codeVerifier, String redirectUri);

  // Keycloak에 사용자 생성
  String createUser(String email, String password);

  // Keycloak에 관리자 사용자 생성 (admin role 부여)
  String createAdminUser(String email, String password);

  // Keycloak 사용자 비밀번호 재설정 (현재 비밀번호 불필요)
  void resetPassword(String keycloakId, String newPassword);

  // Keycloak 사용자 삭제
  void deleteUser(String keycloakId);

  // 이메일로 Keycloak 사용자 조회
  String findUserByEmail(String email);

  // Keycloak 사용자에게 realm role 할당
  void assignRealmRole(String keycloakId, String roleName);

  // Refresh token 으로 새 토큰을 받는다. Keycloak 이 주는 새 refresh token 도 같이 돌려준다
  TokenResponseDto refreshToken(String refreshToken);

  // Refresh token 이 속한 Keycloak 세션을 끊는다. 이미 끝난 세션이면 조용히 넘어간다
  void logout(String refreshToken);
}