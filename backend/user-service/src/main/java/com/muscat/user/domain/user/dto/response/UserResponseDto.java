package com.muscat.user.domain.user.dto.response;

import com.muscat.user.common.enums.type.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "사용자 정보")
public record UserResponseDto(
  @Schema(description = "사용자 ID", example = "12345")
  Long id,

  @Schema(description = "이메일", example = "user@example.com")
  String email,

  @Schema(description = "닉네임", example = "김투자자")
  String nickname,

  @Schema(description = "사용자 권한", example = "USER", allowableValues = {"USER", "ADMIN"})
  UserRole role,

  @Schema(description = "계정 생성일시", example = "2024-09-18T10:30:00")
  LocalDateTime createdAt,

  @Schema(description = "로그인 제공자", example = "LOCAL", allowableValues = {"LOCAL", "GOOGLE"})
  String provider,

  @Schema(description = "이메일 인증 여부", example = "true")
  boolean emailVerified,

  @Schema(description = "프로필 이미지 URL", example = "https://example.com/profile.jpg")
  String profileImageUrl,

  @Schema(description = "소셜 계정 이메일", example = "user@gmail.com")
  String socialEmail
) {

}
