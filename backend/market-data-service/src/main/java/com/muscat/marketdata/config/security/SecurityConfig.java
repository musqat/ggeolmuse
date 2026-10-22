package com.muscat.marketdata.config.security;

import com.muscat.commonlib.security.KeycloakJwtAuthenticationConverter;
import com.muscat.commonlib.security.ServiceSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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

  // Keycloak JWT를 Spring Security 권한으로 변환
  @Bean
  public KeycloakJwtAuthenticationConverter jwtAuthenticationConverter() {
    return new KeycloakJwtAuthenticationConverter();
  }

  @Bean
  @Order(0)
  public SecurityFilterChain managementSecurityFilterChain(HttpSecurity http) throws Exception {
    return ServiceSecurity.managementChain(http);
  }

  // Application port (8083) - Public and authenticated endpoints
  @Bean
  @Order(1)
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    ServiceSecurity.statelessApi(http)
      .authorizeHttpRequests(auth -> auth
        // Public endpoints - 공개 시장 데이터 API
        .requestMatchers("/api/market/**").permitAll()
        .requestMatchers("/api/internal/**").permitAll()  // Gateway RewritePath로 변경된 경로
        .requestMatchers(ServiceSecurity.SWAGGER_PATHS).permitAll()
        // Admin endpoints - ADMIN 권한 필요
        .requestMatchers("/api/admin/**").hasAuthority("admin")
        // Private endpoints - JWT 인증 필요
        .requestMatchers("/api/**").authenticated()
        .anyRequest().denyAll()
      )
      .oauth2ResourceServer(oauth2 -> oauth2
        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
      );
    return http.build();
  }
}
