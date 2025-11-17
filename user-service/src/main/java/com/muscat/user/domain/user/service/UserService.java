package com.muscat.user.domain.user.service;

import com.muscat.user.common.enums.type.UserRole;
import com.muscat.user.domain.user.dto.request.UpdateProfileRequestDto;
import com.muscat.user.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

  // 회원가입 + 이메일 토큰 생성 + 메일 발송
  User registerUser(String email, String password, String nickname);

  // Keycloak OAuth 사용자 동기화 (Google OAuth 등)
  User createUserFromKeycloak(String keycloakId, String email, String nickname);

  // 이메일 토큰 검증 + 사용자 활성화 +  이메일 토큰 삭제
  User verifyEmail(String token);

  // 기존 이메일 토큰 삭제 + 새 이메일 토큰 생성 + 메일 재발송
  void resendVerificationEmail(String email);

  // 비밀번호 재설정 요청 (토큰 생성 + 이메일 발송)
  void requestPasswordReset(String email);

  // 비밀번호 재설정 (토큰 검증 + 비밀번호 변경)
  void resetPassword(String token, String newPassword);

  // 로그인
  String login(String email, String password);

  // 토큰 갱신
  String refreshToken(String refreshToken);

  // 조회
  User getProfile(String email);

  // 닉네임 변경
  User updateProfile(String email, UpdateProfileRequestDto request);

  // 회원 탈퇴
  void deleteAccount(String email, String password);

  // ==================== ADMIN APIs ====================

  // 전체 사용자 목록 조회 (페이징)
  Page<User> getAllUsers(Pageable pageable);

  // 사용자 ID로 조회
  User findById(Long userId);

  // 사용자 역할 변경
  User updateUserRole(Long userId, UserRole role);

  // 사용자 활성화/비활성화
  User updateUserEnabled(Long userId, boolean enabled);

  // Admin: 강제 닉네임 변경
  User adminUpdateNickname(Long userId, String nickname);

  // Admin: 강제 비밀번호 변경
  void adminUpdatePassword(Long userId, String newPassword);

  // Admin: 이메일 강제 인증
  User adminVerifyEmail(Long userId);

  // Admin: 강제 탈퇴
  void adminDeleteUser(Long userId);

  // 통계
  long countTotalUsers();

  long countActiveUsers();

  long countAdminUsers();

}
