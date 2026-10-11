package com.muscat.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

  private Oauth oauth = new Oauth();
  private Mail mail = new Mail();
  private Frontend frontend = new Frontend();

  @Data
  public static class Oauth {

    private String redirectUri;

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
}