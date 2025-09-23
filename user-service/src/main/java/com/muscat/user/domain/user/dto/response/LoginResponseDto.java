package com.muscat.user.domain.user.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "로그인 응답 정보")
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class LoginResponseDto {
  @Schema(description = "액세스 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
  private String accessToken;

  @Schema(description = "리프레시 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
  private String refreshToken;

  @Schema(description = "토큰 만료 시간 (초)", example = "3600")
  private int expiresIn;

  @Schema(description = "토큰 타입", example = "Bearer")
  private String tokenType;
}
