package com.muscat.user.config.security;

import com.muscat.commonlib.security.KeycloakJwtAuthenticationConverter;
import com.muscat.commonlib.security.ServiceSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public JwtDecoder jwtDecoder(Environment environment) {
    return ServiceSecurity.keycloakJwtDecoder(environment);
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  //Keycloak JWT를 Spring Security 권한으로 변환
  @Bean
  public KeycloakJwtAuthenticationConverter jwtAuthenticationConverter() {
    return new KeycloakJwtAuthenticationConverter();
  }

  // Public endpoints를 Security filter chain에서 완전히 제외
  @Bean
  public WebSecurityCustomizer webSecurityCustomizer() {
    return (web) -> web.ignoring()
      .requestMatchers("/api/auth/**")
      .requestMatchers(ServiceSecurity.SWAGGER_PATHS);
  }

  @Bean
  @Order(0)
  public SecurityFilterChain managementSecurityFilterChain(HttpSecurity http) throws Exception {
    return ServiceSecurity.managementChain(http);
  }

  @Bean
  @Order(1)
  public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
    return ServiceSecurity.adminChain(http, jwtAuthenticationConverter());
  }

  // Private endpoints (8080) - JWT authentication required
  @Bean
  @Order(2)
  public SecurityFilterChain privateSecurityFilterChain(HttpSecurity http) throws Exception {
    ServiceSecurity.statelessApi(http)
      .securityMatcher("/api/users/**", "/api/accounts/**")
      .authorizeHttpRequests(auth -> auth
        .anyRequest().authenticated()
      )
      .oauth2ResourceServer(oauth2 -> oauth2
        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
      );
    return http.build();
  }
}
