package com.muscat.user.domain.user.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 응답 정보")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record LoginResponseDto(
  @Schema(description = "액세스 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
  String accessToken,

  @Schema(description = "리프레시 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
  String refreshToken,

  @Schema(description = "토큰 만료 시간 (초)", example = "3600")
  int expiresIn,

  @Schema(description = "토큰 타입", example = "Bearer")
  String tokenType
) {

}
