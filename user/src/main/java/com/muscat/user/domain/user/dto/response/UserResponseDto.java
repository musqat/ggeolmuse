package com.muscat.user.domain.user.dto.response;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponseDto {

  // 기본 정보
  private Long id;
  private String email;
  private String nickname;
  private LocalDateTime createdAt;

  // 인증 정보
  private String provider;             // 로그인 제공자 (LOCAL, GOOGLE)
  private boolean emailVerified;       // 이메일 인증 여부
  
  // 프로필 정보
  private String profileImageUrl;      // 프로필 이미지 URL
  private String socialEmail;          // 소셜 계정 이메일
}