package com.muscat.user.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

// Keycloak 에서 받은 토큰(LoginResponseDto 에서 쓰는 값만). 서비스 안에서 쓰고, 컨트롤러가 refresh token 은 httpOnly 쿠키로 ·
// access token 은 본문(AccessTokenResponseDto)으로 나눠 준다
@Schema(description = "토큰")
public record TokenResponseDto(
  @Schema(description = "액세스 토큰", example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")
  String accessToken,

  @Schema(description = "리프레시 토큰. 갱신할 때마다 새로 받는다", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
  String refreshToken,

  @Schema(description = "액세스 토큰 만료까지 남은 초", example = "900")
  int expiresIn,

  @Schema(description = "리프레시 토큰 만료까지 남은 초", example = "7200")
  int refreshExpiresIn
) {

  public static TokenResponseDto from(LoginResponseDto keycloakResponse) {
    return new TokenResponseDto(
      keycloakResponse.accessToken(),
      keycloakResponse.refreshToken(),
      keycloakResponse.expiresIn(),
      keycloakResponse.refreshExpiresIn());
  }
}
