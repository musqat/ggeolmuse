package com.muscat.commonlib.security;

import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

// 서블릿 서비스가 같이 쓰는 보안 설정 조각(서비스는 경로 규칙만 정함)
public final class ServiceSecurity {

  // API 문서 화면 경로
  public static final String[] SWAGGER_PATHS = {
    "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**"
  };

  private ServiceSecurity() {
  }

  // KEYCLOAK_AUTH_SERVER_URL · KEYCLOAK_REALM 의 JWKS 로 서명을 검사하는 디코더
  public static JwtDecoder keycloakJwtDecoder(Environment environment) {
    String authServerUrl = environment.getProperty("KEYCLOAK_AUTH_SERVER_URL", "http://localhost:8080");
    String realm = environment.getProperty("KEYCLOAK_REALM", "muscathan");
    String jwkSetUri = authServerUrl + "/realms/" + realm + "/protocol/openid-connect/certs";
    return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
  }

  // 관리 포트로 받은 요청(헬스 · 지표)은 인증 없이 엶
  public static SecurityFilterChain managementChain(HttpSecurity http) throws Exception {
    int managementPort = managementPort(http.getSharedObject(ApplicationContext.class).getEnvironment());
    http
      .securityMatcher(request -> request.getLocalPort() == managementPort)
      .authorizeHttpRequests(auth -> auth
        .anyRequest().permitAll()
      )
      .csrf(AbstractHttpConfigurer::disable)
      .headers(headers -> headers
        .frameOptions(FrameOptionsConfig::disable)
      );
    return http.build();
  }

  // API 포트와 따로 둔 관리 포트(설정이 없거나 API 포트와 같으면 -1)
  static int managementPort(Environment environment) {
    Integer port = environment.getProperty("management.server.port", Integer.class);
    int serverPort = environment.getProperty("server.port", Integer.class, 8080);
    return port == null || port == serverPort ? -1 : port;
  }

  // API 체인 바탕: CSRF 끔 · CORS 기본 · 세션 없음 · 프레임 옵션 끔
  public static HttpSecurity statelessApi(HttpSecurity http) throws Exception {
    return http
      .csrf(AbstractHttpConfigurer::disable)
      .cors(Customizer.withDefaults())
      .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .headers(headers -> headers
        .frameOptions(FrameOptionsConfig::disable)
      );
  }

  // /api/admin/** 는 Keycloak realm 역할 admin 이 있어야 함
  public static SecurityFilterChain adminChain(HttpSecurity http, KeycloakJwtAuthenticationConverter converter)
    throws Exception {
    statelessApi(http)
      .securityMatcher("/api/admin/**")
      .authorizeHttpRequests(auth -> auth
        .anyRequest().hasAuthority("admin")
      )
      .oauth2ResourceServer(oauth2 -> oauth2
        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
      );
    return http.build();
  }
}
