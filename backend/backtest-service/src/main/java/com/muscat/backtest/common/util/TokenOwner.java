package com.muscat.backtest.common.util;

import org.springframework.security.oauth2.jwt.Jwt;

/** 백테스트 이력 주인(토큰의 subject, 이메일은 옛 행을 옮길 때만) */
public final class TokenOwner {

  private TokenOwner() {
  }

  public static String sub(Jwt jwt) {
    if (jwt == null) {
      return null;
    }
    String sub = jwt.getSubject();
    return sub == null || sub.isBlank() ? null : sub;
  }

  public static String email(Jwt jwt) {
    if (jwt == null) {
      return null;
    }
    String email = jwt.getClaimAsString("email");
    return email == null || email.isBlank() ? null : email;
  }
}
