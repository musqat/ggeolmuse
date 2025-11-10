package com.muscat.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Gateway Rate Limiter 설정
 * Redis 기반 분산 Rate Limiting
 */
@Configuration
public class RateLimiterConfig {

    /**
     * IP 주소 기반 Rate Limiting
     * 동일 IP에서 오는 요청을 그룹화하여 제한
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String clientIp = Objects.requireNonNull(
                exchange.getRequest().getRemoteAddress()
            ).getAddress().getHostAddress();

            return Mono.just(clientIp);
        };
    }

    /**
     * User ID 기반 Rate Limiting (JWT 토큰에서 추출)
     * 인증된 사용자별로 별도 제한 적용 가능 (선택적 사용)
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
            .map(principal -> principal.getName())
            .defaultIfEmpty("anonymous");
    }

    /**
     * API Path 기반 Rate Limiting
     * 특정 경로별로 다른 제한 적용 (선택적 사용)
     */
    @Bean
    public KeyResolver pathKeyResolver() {
        return exchange -> {
            String path = exchange.getRequest().getPath().value();
            return Mono.just(path);
        };
    }
}
