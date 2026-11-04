package com.muscat.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

  private Oauth oauth = new Oauth();
  private Auth auth = new Auth();

  @Data
  public static class Oauth {

    // 브라우저가 구글 로그인(PKCE)으로 토큰을 받는 public client(받은 refresh token 의 갱신 · 로그아웃도 이 client, 프론트 VITE_KEYCLOAK_CLIENT_ID 와 같음)
    private String publicClientId = "ggeolmuse-frontend";
  }

  // refresh token 쿠키(RefreshTokenCookie)
  @Data
  public static class Auth {

    // 브라우저 쪽 경로와 같음(게이트웨이가 경로를 그대로 넘김)
    private String refreshCookiePath = "/api/auth";

    // https 에서만 전송(브라우저는 http://localhost 도 안전한 출처로 봐 로컬 개발에서도 실림)
    private boolean refreshCookieSecure = true;
  }
}