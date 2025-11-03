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
    return environment.getProperty("KEYCLOAK_REALM", "muscathan");
  }

  @Bean
  public JwtDecoder jwtDecoder() {
    String authServerUrl = getKeycloakAuthServerUrl();
    String realm = getKeycloakRealm();
    String jwkSetUri = authServerUrl + "/realms/" + realm + "/protocol/openid-connect/certs";
    return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
  }

  // Management port (9090) - Health, Metrics, Actuator endpoints
  @Bean
  @Order(0)
  public SecurityFilterChain managementSecurityFilterChain(HttpSecurity http) throws Exception {
    http
      .securityMatcher(request -> request.getServerPort() == 9090)
      .authorizeHttpRequests(auth -> auth
        .anyRequest().permitAll()
      )
      .csrf(AbstractHttpConfigurer::disable)
      .headers(headers -> headers
        .frameOptions(FrameOptionsConfig::disable)
      );
    return http.build();
  }

  // Application port (8083) - Public and authenticated endpoints
  @Bean
  @Order(1)
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
      .csrf(AbstractHttpConfigurer::disable)
      .cors(Customizer.withDefaults())
      .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth -> auth
        // Public endpoints - 공개 시장 데이터 API
        .requestMatchers("/api/market/**").permitAll()
        .requestMatchers("/api/internal/**").permitAll()  // Gateway RewritePath로 변경된 경로
        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
          "/swagger-resources/**", "/webjars/**").permitAll()
        // Private endpoints - JWT 인증 필요
        .requestMatchers("/api/**").authenticated()
        .anyRequest().denyAll()
      )
      .oauth2ResourceServer(oauth2 -> oauth2
        .jwt(Customizer.withDefaults())
      )
      .headers(headers -> headers
        .frameOptions(FrameOptionsConfig::disable)
      );
    return http.build();
  }

}
