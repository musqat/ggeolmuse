package com.muscat.user.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

// 로그인 · 갱신 응답 본문. refresh token 은 본문이 아니라 httpOnly 쿠키(RefreshTokenCookie)로 준다
@Schema(description = "액세스 토큰 응답")
public record AccessTokenResponseDto(
  @Schema(description = "액세스 토큰", example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")
  String accessToken,

  @Schema(description = "액세스 토큰 만료까지 남은 초", example = "900")
  int expiresIn
) {

  public static AccessTokenResponseDto from(TokenResponseDto tokens) {
    return new AccessTokenResponseDto(tokens.accessToken(), tokens.expiresIn());
  }
}
