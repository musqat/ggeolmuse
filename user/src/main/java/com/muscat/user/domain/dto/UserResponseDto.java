package com.muscat.user.domain.dto;

import com.muscat.user.domain.entity.User;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponseDto {

  private Long id;
  private String email;
  private String nickname;
  private String provider;
  private String profileImageUrl;
  private boolean emailVerified;
  private String socialEmail;  // 소셜 계정 이메일
  private LocalDateTime createdAt;  // 가입일

  public static UserResponseDto from(User user) {
    return UserResponseDto.builder()
        .id(user.getId())
        .email(user.getEmail())
        .nickname(user.getNickname())
        .provider(user.getProvider().toString())
        .profileImageUrl(user.getProfileImageUrl())
        .emailVerified(user.isEmailVerified())
        .socialEmail(user.getSocialEmail())
        .createdAt(user.getCreatedAt())
        .build();
  }
}