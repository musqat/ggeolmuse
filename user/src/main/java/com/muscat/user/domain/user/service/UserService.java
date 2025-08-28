package com.muscat.user.domain.user.service;

import com.muscat.user.domain.user.dto.request.UpdateProfileRequestDto;
import com.muscat.user.domain.user.entity.User;

public interface UserService {

  // 회원가입 + 이메일 토큰 생성 + 메일 발송
  User registerUser(String email, String password, String nickname);

  // 이메일 토큰 검증 + 사용자 활성화 +  이메일 토큰 삭제
  User verifyEmail(String token);

  // 기존 이메일 토큰 삭제 + 새 이메일 토큰 생성 + 메일 재발송
  void resendVerificationEmail(String email);

  // 로그인
  String login(String email, String password);

  // 조회
  User getProfile(String email);

  // 닉네임 변경
  User updateProfile(String email, UpdateProfileRequestDto request);

  // 회원 탈퇴
  void deleteAccount(String email, String password);

}
