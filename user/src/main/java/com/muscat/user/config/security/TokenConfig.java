package com.muscat.user.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class TokenConfig {

  @Value("${keycloak.auth-server-url}")
  private String keycloakUrl;

  @Value("${keycloak.realm}")
  private String realm;

  @Bean
  public JwtDecoder jwtDecoder() {
    String jwkSetUri = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/certs";
    return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
  }
}