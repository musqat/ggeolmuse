package com.muscat.commonlib.security;

import java.util.List;
import java.util.stream.Stream;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

// 서블릿 서비스가 같이 쓰는 보안 설정 조각(서비스는 경로 규칙만 정함)
public final class ServiceSecurity {

  // API 문서 화면 경로
  public static final String[] SWAGGER_PATHS = {
    "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**"
  };

  // 서비스 토큰(서비스 클라이언트의 서비스 계정)이 가진 Keycloak realm 역할
  public static final String SERVICE_ROLE = "service";

  // 받는 토큰의 azp 기본값(이메일 로그인 · 구글 로그인 클라이언트)
  private static final String[] LOGIN_CLIENT_IDS = {"ggeolmuse-client", "ggeolmuse-frontend"};

  private ServiceSecurity() {
  }

  // realm JWKS 로 서명을 검사하고 iss · azp 허용 목록(keycloak.issuers · keycloak.client-ids · 서비스 클라이언트)과 이메일 인증을 보는 디코더
  public static JwtDecoder keycloakJwtDecoder(Environment environment) {
    String authServerUrl = environment.getProperty("KEYCLOAK_AUTH_SERVER_URL", "http://localhost:8080");
    String realm = environment.getProperty("KEYCLOAK_REALM", "muscathan");
    String realmUrl = authServerUrl + "/realms/" + realm;
    List<String> issuers = List.of(environment.getProperty("keycloak.issuers", String[].class, new String[]{realmUrl}));
    List<String> loginClientIds = List.of(environment.getProperty("keycloak.client-ids", String[].class, LOGIN_CLIENT_IDS));
    List<String> clientIds = Stream.concat(loginClientIds.stream(), Stream.of(ServiceTokenProvider.clientId(environment))).toList();

    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(realmUrl + "/protocol/openid-connect/certs").build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(List.of(
      new JwtClaimValidator<Object>(JwtClaimNames.ISS, iss -> iss != null && issuers.contains(iss.toString())),
      new JwtClaimValidator<Object>("azp", azp -> azp != null && clientIds.contains(azp.toString())),
      emailVerified(loginClientIds))));
    return decoder;
  }

  // 로그인 클라이언트로 받은 토큰은 email_verified 가 true 일 때만 통과
  private static OAuth2TokenValidator<Jwt> emailVerified(List<String> loginClientIds) {
    OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "The email_verified claim is not valid", null);
    return jwt -> {
      String azp = jwt.getClaimAsString("azp");
      boolean loginToken = azp != null && loginClientIds.contains(azp);
      return !loginToken || Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(error);
    };
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

  // /api/internal/** 는 Keycloak realm 역할 service(서비스 토큰)가 있어야 함
  public static SecurityFilterChain internalChain(HttpSecurity http, KeycloakJwtAuthenticationConverter converter)
    throws Exception {
    statelessApi(http)
      .securityMatcher("/api/internal/**")
      .authorizeHttpRequests(auth -> auth
        .anyRequest().hasAuthority(SERVICE_ROLE)
      )
      .oauth2ResourceServer(oauth2 -> oauth2
        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
      );
    return http.build();
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
