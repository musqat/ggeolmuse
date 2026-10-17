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

// Gateway Rate Limiter 설정(Redis 기반 분산)
@Configuration
public class RateLimiterConfig {

    private final XForwardedRemoteAddressResolver xForwardedResolver =
        XForwardedRemoteAddressResolver.maxTrustedIndex(1);

    /** 실 IP 나 라우트를 못 구했을 때 쓰는 키 */
    private static final String UNKNOWN_CLIENT = "unknown";

    // XFF 의 실 IP 로 나눔(소켓 주소면 nginx 뒤에서 다같이 한 버킷)
    // 키에 라우트 ID 를 붙임. IP 만 쓰면 상한 낮은 라우트가 다른 라우트의 토큰을 깎음
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

    // JWT 사용자별 키(골라서 씀)
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
            .map(principal -> principal.getName())
            .defaultIfEmpty("anonymous");
    }

    // 경로별 키(골라서 씀)
    @Bean
    public KeyResolver pathKeyResolver() {
        return exchange -> {
            String path = exchange.getRequest().getPath().value();
            return Mono.just(path);
        };
    }
}
