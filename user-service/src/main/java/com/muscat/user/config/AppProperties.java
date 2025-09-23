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