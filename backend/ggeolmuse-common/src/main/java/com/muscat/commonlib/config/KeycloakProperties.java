package com.muscat.commonlib.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakProperties {

  private String authServerUrl;
  private String realm;
  private String resource;
  private Credentials credentials = new Credentials();
  private Admin admin = new Admin();

  @Data
  public static class Credentials {

    private String secret;
  }

  @Data
  public static class Admin {

    private String username;
    private String password;
  }
}
