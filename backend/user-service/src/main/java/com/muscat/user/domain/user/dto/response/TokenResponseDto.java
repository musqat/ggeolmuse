package com.muscat.user.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

// 로그인 · 토큰 갱신 응답. Keycloak 응답(LoginResponseDto, snake_case)에서 프론트가 쓰는 값만 옮긴다
@Schema(description = "토큰 응답")
public record TokenResponseDto(
  @Schema(description = "액세스 토큰", example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")
  String accessToken,

  @Schema(description = "리프레시 토큰. 갱신할 때마다 새로 받는다", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
  String refreshToken,

  @Schema(description = "액세스 토큰 만료까지 남은 초", example = "3600")
  int expiresIn
) {

  public static TokenResponseDto from(LoginResponseDto keycloakResponse) {
    return new TokenResponseDto(
      keycloakResponse.accessToken(),
      keycloakResponse.refreshToken(),
      keycloakResponse.expiresIn());
  }
}
