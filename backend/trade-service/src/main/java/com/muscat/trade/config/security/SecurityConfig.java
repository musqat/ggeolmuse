package com.muscat.trade.config.security;

import com.muscat.commonlib.security.KeycloakJwtAuthenticationConverter;
import com.muscat.commonlib.security.ServiceSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
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

  @Bean
  @Order(0)
  public SecurityFilterChain managementSecurityFilterChain(HttpSecurity http) throws Exception {
    return ServiceSecurity.managementChain(http);
  }

  @Bean
  public KeycloakJwtAuthenticationConverter jwtAuthenticationConverter() {
    return new KeycloakJwtAuthenticationConverter();
  }

  @Bean
  @Order(1)
  public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
    return ServiceSecurity.adminChain(http, jwtAuthenticationConverter());
  }

  // Application port (8081) - JWT authentication required
  @Bean
  @Order(2)
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    ServiceSecurity.statelessApi(http)
      .authorizeHttpRequests(auth -> auth
        .requestMatchers(ServiceSecurity.SWAGGER_PATHS).permitAll()
        // All API endpoints - JWT 인증 필요 (FeignClient도 JWT 전달)
        .requestMatchers("/api/**").authenticated()
        // 나머지는 모두 거부
        .anyRequest().denyAll()
      )
      .oauth2ResourceServer(oauth2 -> oauth2
        .jwt(Customizer.withDefaults())
      );
    return http.build();
  }
}
