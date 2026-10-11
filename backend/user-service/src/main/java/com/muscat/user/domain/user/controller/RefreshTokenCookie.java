package com.muscat.user.domain.user.controller;

import com.muscat.user.config.AppProperties;
import com.muscat.user.domain.user.dto.response.TokenResponseDto;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

// refresh token 을 담는 쿠키. HttpOnly 라 JS(XSS)가 못 읽고, SameSite=Strict 라 다른 사이트에서 온 요청에는 안 실린다.
// Path 가 /api/auth 라 로그인 · 갱신 · 로그아웃 요청에만 실린다
@Component
@RequiredArgsConstructor
public class RefreshTokenCookie {

  public static final String NAME = "refresh_token";

  private final AppProperties appProperties;

  // 새 refresh token 을 심는다. 만료는 Keycloak 이 준 refresh_expires_in 을 따른다
  public String issue(TokenResponseDto tokens) {
    int seconds = tokens.refreshExpiresIn();
    // 0 이하면 만료를 모르는 것이라 브라우저 세션 쿠키로 둔다(Max-Age 0 은 지우기다)
    return build(tokens.refreshToken(), seconds > 0 ? Duration.ofSeconds(seconds) : Duration.ofSeconds(-1));
  }

  // 쿠키를 지운다
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
