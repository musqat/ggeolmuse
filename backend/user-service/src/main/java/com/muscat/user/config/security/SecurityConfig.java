package com.muscat.user.config.security;

import com.muscat.commonlib.security.KeycloakJwtAuthenticationConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
      .requestMatchers(
        "/api/auth/**",
        "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
        "/swagger-resources/**", "/webjars/**");
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

  // Admin endpoints (8080) - JWT authentication + ADMIN role required
  @Bean
  @Order(1)
  public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
    http
      .securityMatcher("/api/admin/**")
      .csrf(AbstractHttpConfigurer::disable)
      .cors(Customizer.withDefaults())
      .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth -> auth
        .anyRequest().hasAuthority("admin")  // Keycloak realm_access.roles의 "admin" 체크
      )
      .oauth2ResourceServer(oauth2 -> oauth2
        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
      )
      .headers(headers -> headers
        .frameOptions(FrameOptionsConfig::disable)
      );
    return http.build();
  }

  // Private endpoints (8080) - JWT authentication required
  @Bean
  @Order(2)
  public SecurityFilterChain privateSecurityFilterChain(HttpSecurity http) throws Exception {
    http
      .securityMatcher("/api/users/**", "/api/accounts/**")
      .csrf(AbstractHttpConfigurer::disable)
      .cors(Customizer.withDefaults())
      .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth -> auth
        .anyRequest().authenticated()
      )
      .oauth2ResourceServer(oauth2 -> oauth2
        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
      )
      .headers(headers -> headers
        .frameOptions(FrameOptionsConfig::disable)
      );
    return http.build();
  }


}
