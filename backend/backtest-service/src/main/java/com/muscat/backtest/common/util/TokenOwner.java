package com.muscat.backtest.common.util;

import org.springframework.security.oauth2.jwt.Jwt;

/** 백테스트 이력 주인(토큰의 이메일). 이력 행의 userId 가 이메일이라 subject 는 안 씀 */
public final class TokenOwner {

  private TokenOwner() {
  }

  public static String email(Jwt jwt) {
    if (jwt == null) {
      return null;
    }
    String email = jwt.getClaimAsString("email");
    return email == null || email.isBlank() ? null : email;
  }
}
