package com.muscat.backtest.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
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

  // Application port (8082) - JWT authentication required
  @Bean
  @Order(1)
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                "/swagger-resources/**", "/webjars/**").permitAll()
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
