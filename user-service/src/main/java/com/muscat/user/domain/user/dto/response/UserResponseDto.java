package com.muscat.user.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "사용자 정보")
@Getter
@Builder
public class UserResponseDto {

  // 기본 정보
  @Schema(description = "사용자 ID", example = "12345")
  private Long id;

  @Schema(description = "이메일", example = "user@example.com")
  private String email;

  @Schema(description = "닉네임", example = "김투자자")
  private String nickname;

  @Schema(description = "계정 생성일시", example = "2024-09-18T10:30:00")
  private LocalDateTime createdAt;

  // 인증 정보
  @Schema(description = "로그인 제공자", example = "LOCAL", allowableValues = {"LOCAL", "GOOGLE"})
  private String provider;             // 로그인 제공자 (LOCAL, GOOGLE)

  @Schema(description = "이메일 인증 여부", example = "true")
  private boolean emailVerified;       // 이메일 인증 여부

  // 프로필 정보
  @Schema(description = "프로필 이미지 URL", example = "https://example.com/profile.jpg")
  private String profileImageUrl;      // 프로필 이미지 URL

  @Schema(description = "소셜 계정 이메일", example = "user@gmail.com")
  private String socialEmail;          // 소셜 계정 이메일
}