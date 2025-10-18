package com.muscat.marketdata.config.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Autowired
  private Environment environment;

  private String getKeycloakAuthServerUrl() {
    return environment.getProperty("KEYCLOAK_AUTH_SERVER_URL", "http://localhost:8080");
  }

  private String getKeycloakRealm() {
    return environment.getProperty("KEYCLOAK_REALM", "ggeolmuse");
  }

  @Bean
  public JwtDecoder jwtDecoder() {
    String authServerUrl = getKeycloakAuthServerUrl();
    String realm = getKeycloakRealm();
    String jwkSetUri = authServerUrl + "/realms/" + realm + "/protocol/openid-connect/certs";
    return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
  }

  // Public endpoints - 완전히 보안 없음
  @Bean
  @Order(1)
  public SecurityFilterChain publicSecurityFilterChain(HttpSecurity http) throws Exception {
    http
        .securityMatcher(
            "/api/market/public/**",
            "/h2-console/**",
            "/actuator/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/webjars/**"
        )
        .csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .headers(headers -> headers.frameOptions(FrameOptionsConfig::disable));

    return http.build();
  }

  // Private endpoints - OAuth2 JWT 인증 필수
  @Bean
  @Order(2)
  public SecurityFilterChain privateSecurityFilterChain(HttpSecurity http) throws Exception {
    http
        .securityMatcher("/api/**")
        .csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
        .headers(headers -> headers.frameOptions(FrameOptionsConfig::disable));

    return http.build();
  }

}
