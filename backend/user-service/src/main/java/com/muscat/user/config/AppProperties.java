package com.muscat.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

  private Oauth oauth = new Oauth();
  private Mail mail = new Mail();
  private Frontend frontend = new Frontend();
  private Auth auth = new Auth();

  @Data
  public static class Oauth {

    // 브라우저가 구글 로그인(PKCE)으로 토큰을 받는 public client. 이 client 가 받은 refresh token 은
    // 같은 client 로 갱신 · 로그아웃해야 Keycloak 이 받는다(프론트 VITE_KEYCLOAK_CLIENT_ID 와 같다)
    private String publicClientId = "ggeolmuse-frontend";
  }

  @Data
  public static class Mail {

    private String from;
    private Verification verification = new Verification();

    @Data
    public static class Verification {

      private String baseUrl;
      private int expiryHours;
    }
  }

  @Data
  public static class Frontend {

    private String url;
  }

  // refresh token 쿠키(RefreshTokenCookie)
  @Data
  public static class Auth {

    // 게이트웨이가 경로를 그대로 넘기므로 브라우저 쪽 경로와 같다
    private String refreshCookiePath = "/api/auth";

    // https 에서만 보낸다. 브라우저는 http://localhost 도 안전한 출처로 봐 로컬 개발에서도 실린다
    private boolean refreshCookieSecure = true;
  }
}