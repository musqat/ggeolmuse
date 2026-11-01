package com.muscat.user.domain.user.service;

import com.muscat.user.domain.user.dto.response.TokenResponseDto;

// Keycloak 인증 서비스 인터페이스
public interface KeycloakService {

  // 이메일 · 비밀번호로 Keycloak 로그인(access · refresh token 반환)
  TokenResponseDto login(String email, String password);

  // 브라우저가 public client 로 시작한 구글 로그인(PKCE)의 인가 코드 → 토큰
  TokenResponseDto exchangePkceCode(String code, String codeVerifier, String redirectUri);

  // Keycloak에 사용자 생성
  String createUser(String email, String password);

  // Keycloak에 관리자 사용자 생성 (admin role 부여)
  String createAdminUser(String email, String password);

  // Keycloak 사용자 비밀번호 재설정 (현재 비밀번호 불필요)
  void resetPassword(String keycloakId, String newPassword);

  // Keycloak 사용자 활성 상태 변경(끄면 새 로그인 · 토큰 갱신 차단)
  void setEnabled(String keycloakId, boolean enabled);

  // Keycloak 사용자 삭제
  void deleteUser(String keycloakId);

  // 사용자 이름이 정확히 같은 Keycloak 사용자 ID. 없으면 null, 조회가 실패하면 예외
  String findUserIdByUsername(String username);

  // Keycloak 사용자에게 realm role 할당
  void assignRealmRole(String keycloakId, String roleName);

  // Keycloak 사용자의 realm role 해제
  void removeRealmRole(String keycloakId, String roleName);

  // 사용자의 Keycloak 세션 전체 종료(이미 받은 refresh token 으로 새 토큰을 못 받음)
  void logoutUserSessions(String keycloakId);

  // Refresh token 으로 새 토큰 발급(Keycloak 이 주는 새 refresh token 포함)
  TokenResponseDto refreshToken(String refreshToken);

  // Refresh token 이 속한 Keycloak 세션 종료(이미 끝난 세션이면 무시)
  void logout(String refreshToken);

  // 서비스 토큰을 한 번 받아 토큰 발급 경로 · 연결을 열어 둠
  void warmUp();
}