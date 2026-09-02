package com.muscat.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Gateway Rate Limiter 설정
 * Redis 기반 분산 Rate Limiting
 */
@Configuration
public class RateLimiterConfig {

    private final XForwardedRemoteAddressResolver xForwardedResolver =
        XForwardedRemoteAddressResolver.maxTrustedIndex(1);

    /** 실 IP 나 라우트를 못 구했을 때 쓰는 키 */
    private static final String UNKNOWN_CLIENT = "unknown";

    /**
     * XFF 를 신뢰해 실 IP 로 나눈다. 소켓 주소만 보면 nginx 뒤에서 다같이 한 버킷을 쓴다.
     *
     * 라우트 ID 를 붙이는 이유 - Lua 스크립트가 저장된 토큰을 호출한 라우트의
     * burstCapacity 로 자른다. IP 만 키로 쓰면 상한 낮은 라우트가 나머지 토큰을 깎는다.
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(clientIp(exchange) + ":" + routeId(exchange));
    }

    /** XFF 우선, 없으면 소켓 주소. 미해석 주소와 끊긴 커넥션에서 둘 다 null 이 된다. */
    private String clientIp(ServerWebExchange exchange) {
        InetSocketAddress resolved = xForwardedResolver.resolve(exchange);
        String ip = hostAddress(resolved);
        if (ip != null) {
            return ip;
        }

        ip = hostAddress(exchange.getRequest().getRemoteAddress());
        return ip != null ? ip : UNKNOWN_CLIENT;
    }

    private String hostAddress(InetSocketAddress socketAddress) {
        if (socketAddress == null) {
            return null;
        }
        InetAddress address = socketAddress.getAddress();
        return address != null ? address.getHostAddress() : null;
    }

    private String routeId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route != null ? route.getId() : UNKNOWN_CLIENT;
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
