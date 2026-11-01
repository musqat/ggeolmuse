package com.muscat.user.domain.user.controller;

import com.muscat.user.config.AppProperties;
import com.muscat.user.domain.user.dto.response.TokenResponseDto;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

// refresh token 쿠키 — HttpOnly · SameSite=Strict · Path /api/auth(로그인 · 갱신 · 로그아웃 요청에만)
@Component
@RequiredArgsConstructor
public class RefreshTokenCookie {

  public static final String NAME = "refresh_token";

  private final AppProperties appProperties;

  // 새 refresh token 쿠키(만료는 Keycloak 이 준 refresh_expires_in)
  public String issue(TokenResponseDto tokens) {
    int seconds = tokens.refreshExpiresIn();
    // 0 이하면 만료를 모르는 것이라 브라우저 세션 쿠키로 둔다(Max-Age 0 은 지우기다)
    return build(tokens.refreshToken(), seconds > 0 ? Duration.ofSeconds(seconds) : Duration.ofSeconds(-1));
  }

  // 쿠키 삭제
  public String clear() {
    return build("", Duration.ZERO);
  }

  private String build(String value, Duration maxAge) {
    AppProperties.Auth auth = appProperties.getAuth();
    return ResponseCookie.from(NAME, value)
      .httpOnly(true)
      .secure(auth.isRefreshCookieSecure())
      .sameSite("Strict")
      .path(auth.getRefreshCookiePath())
      .maxAge(maxAge)
      .build()
      .toString();
  }
}
