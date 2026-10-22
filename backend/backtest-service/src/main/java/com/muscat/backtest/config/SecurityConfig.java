package com.muscat.backtest.config;

import com.muscat.commonlib.security.ServiceSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
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

  // Application port (8082) - JWT authentication required
  @Bean
  @Order(1)
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    ServiceSecurity.statelessApi(http)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers(ServiceSecurity.SWAGGER_PATHS).permitAll()
            // 백테스트(시뮬레이션/분석)는 비로그인 허용 — gateway·프론트와 일치. 히스토리(/api/backtest/**)는 인증 유지
            .requestMatchers(HttpMethod.POST, "/api/trading-simulation/**").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/analysis/**").permitAll()
            .requestMatchers("/api/**").authenticated()
            .anyRequest().denyAll()
        )
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(Customizer.withDefaults())
        );
    return http.build();
  }
}
