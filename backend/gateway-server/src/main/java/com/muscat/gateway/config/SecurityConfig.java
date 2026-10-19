package com.muscat.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import reactor.core.publisher.Mono;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

// Gateway 보안 설정(공개 · 인증 · 관리자 경로와 CORS)
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${keycloak.auth-server-url:http://keycloak:8080}")
    private String keycloakUrl;

    @Value("${keycloak.realm:ggeolmuse}")
    private String realm;

    // 받는 토큰의 iss. 없으면 JWKS 를 받는 내부 주소의 issuer
    @Value("${keycloak.issuers:${keycloak.auth-server-url:http://keycloak:8080}/realms/${keycloak.realm:ggeolmuse}}")
    private List<String> issuers;

    // 받는 토큰의 azp. 이메일 로그인 · 구글 로그인 클라이언트
    @Value("${keycloak.client-ids:ggeolmuse-client,ggeolmuse-frontend}")
    private List<String> clientIds;

    @Value("${gateway.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    // Public endpoints - OAuth2 없이 처리
    @Bean
    @org.springframework.core.annotation.Order(1)
    public SecurityWebFilterChain publicSecurityWebFilterChain(ServerHttpSecurity http) {
        http
            .securityMatcher(exchange -> {
                String path = exchange.getRequest().getPath().value();
                boolean matches = path.startsWith("/api/auth/") ||
                                  path.startsWith("/swagger-ui") ||
                                  path.startsWith("/actuator/") ||
                                  path.startsWith("/health") ||
                                  path.startsWith("/v3/api-docs");
                return matches ?
                    org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult.match() :
                    org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult.notMatch();
            })
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeExchange(exchanges -> exchanges
                .anyExchange().permitAll()
            );

        return http.build();
    }

    // Protected endpoints - OAuth2 JWT 검증
    @Bean
    @org.springframework.core.annotation.Order(2)
    public SecurityWebFilterChain protectedSecurityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeExchange(exchanges -> exchanges
                // CORS Preflight (OPTIONS) 요청 허용
                .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Market data public APIs (시세 조회 등)
                .pathMatchers(HttpMethod.GET, "/api/market/**").permitAll()
                .pathMatchers(HttpMethod.POST, "/api/market/fx/bulk").permitAll()

                // ===== Admin APIs (ADMIN 권한 필요) =====
                .pathMatchers("/api/admin/**").hasAuthority("admin")

                // ===== Backtest public APIs (비로그인 허용) =====
                .pathMatchers(HttpMethod.POST, "/api/trading-simulation/**").permitAll()
                .pathMatchers(HttpMethod.POST, "/api/analysis/**").permitAll()

                // ===== Private APIs (JWT 인증 필요) =====
                .pathMatchers("/api/users/**", "/api/accounts/**").authenticated()
                .pathMatchers("/api/trade/**", "/api/portfolio/**", "/api/transactions/**", "/api/trade-history/**").authenticated()
                .pathMatchers("/api/backtest/**").authenticated()

                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtDecoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    /**
     * JWT Decoder 설정
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        String jwkSetUri = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/certs";
        return buildJwtDecoder(jwkSetUri, issuers, clientIds);
    }

    // 서명 · 만료에 더해 iss · azp 가 허용 목록에 있는 토큰만 통과
    static ReactiveJwtDecoder buildJwtDecoder(String jwkSetUri, List<String> issuers, List<String> clientIds) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(List.of(
            new JwtClaimValidator<Object>(JwtClaimNames.ISS, iss -> iss != null && issuers.contains(iss.toString())),
            // aud 에는 Keycloak 기본값(account)만 실려 받은 클라이언트는 azp 로 봄
            new JwtClaimValidator<Object>("azp", azp -> azp != null && clientIds.contains(azp.toString())))));
        return decoder;
    }

    // Keycloak realm_access.roles 를 권한으로
    @Bean
    public Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        return new KeycloakReactiveJwtAuthenticationConverter();

    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        configuration.setAllowedOrigins(origins);

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

        configuration.setAllowedHeaders(Arrays.asList("*"));

        // 인증 정보 포함 허용 (Credentials)
        configuration.setAllowCredentials(true);

        // Preflight 요청 캐시 시간 (1시간)
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
