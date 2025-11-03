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

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            // CSRF 비활성화 (JWT 사용)
            .csrf(csrf -> csrf.disable())

            // CORS 설정
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 경로별 인증 설정
            .authorizeExchange(exchanges -> exchanges
                // ===== Public APIs (인증 불필요) =====

                // Swagger UI 및 API Docs (모든 사용자 접근 가능)
                .pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/webjars/**").permitAll()

                // Auth endpoints (회원가입, 로그인, OAuth)
                .pathMatchers("/api/auth/**").permitAll()

                // Market data public APIs (시세 조회 등)
                .pathMatchers(HttpMethod.GET, "/api/market/**").permitAll()
                .pathMatchers(HttpMethod.POST, "/api/market/fx/bulk").permitAll()  // Bulk FX rates

                // Actuator health check (liveness/readiness probe)
                .pathMatchers("/health", "/actuator/health").permitAll()


                // ===== Admin APIs (ADMIN 권한 필요) =====
                .pathMatchers("/api/admin/**").hasRole("ADMIN")


                // ===== Private APIs (JWT 인증 필요) =====

                // User & Account management
                .pathMatchers("/api/users/**", "/api/accounts/**").authenticated()

                // Trading & Portfolio
                .pathMatchers("/api/trade/**", "/api/portfolio/**", "/api/transactions/**", "/api/trade-history/**").authenticated()

                // Backtest & Analysis
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
        String issuerUri = keycloakUrl + "/realms/" + realm;
        return NimbusReactiveJwtDecoder.withIssuerLocation(issuerUri).build();
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
