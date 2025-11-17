package com.muscat.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Gateway Security Configuration
 *
 * - Public API: 인증 불필요 (/api/auth/**, /api/market/**, /swagger-ui/**)
 * - Private API: JWT 인증 필요 (/api/users/**, /api/trade/**, /api/portfolio/** 등)
 * - Admin API: ADMIN 권한 필요 (/api/admin/**)
 * - CORS 설정: 프론트엔드 도메인 허용
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${keycloak.auth-server-url:http://keycloak:8080}")
    private String keycloakUrl;

    @Value("${keycloak.realm:ggeolmuse}")
    private String realm;

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
                .pathMatchers("/api/admin/**").hasRole("ADMIN")

                // ===== Private APIs (JWT 인증 필요) =====
                .pathMatchers("/api/users/**", "/api/accounts/**").authenticated()
                .pathMatchers("/api/trade/**", "/api/portfolio/**", "/api/transactions/**", "/api/trade-history/**").authenticated()
                .pathMatchers("/api/backtest/**", "/api/analysis/**", "/api/trading-simulation/**").authenticated()

                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtDecoder(jwtDecoder()))
            );

        return http.build();
    }

    /**
     * JWT Decoder 설정
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        String jwkSetUri = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/certs";
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    /**
     * CORS 설정
     * 프론트엔드에서 Gateway로 Cross-Origin 요청 허용
     */
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
