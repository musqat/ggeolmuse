package com.muscat.user.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Schema(description = "로그인 응답 정보")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record LoginResponseDto(
  @Schema(description = "액세스 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
  String accessToken,

  @Schema(description = "리프레시 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
  String refreshToken,

  @Schema(description = "토큰 만료 시간 (초)", example = "900")
  int expiresIn,

  @Schema(description = "토큰 타입", example = "Bearer")
  String tokenType,

  @Schema(description = "리프레시 토큰 만료 시간 (초)", example = "7200")
  int refreshExpiresIn
) {

}
