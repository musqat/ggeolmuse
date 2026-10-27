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
import java.util.regex.Pattern;

// Gateway Rate Limiter 설정(Redis 기반 분산)
@Configuration
public class RateLimiterConfig {

    private final XForwardedRemoteAddressResolver xForwardedResolver =
        XForwardedRemoteAddressResolver.maxTrustedIndex(1);

    /** Cloudflare 가 실 클라이언트 IP 로 덮어써 넣는 헤더. origin 이 Cloudflare 대역만 받는다는 전제 */
    private static final String CF_CONNECTING_IP = "CF-Connecting-IP";

    /** IPv4 · IPv6 글자만으로 된 값 */
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    /** 실 IP 나 라우트를 못 구했을 때 쓰는 키 */
    private static final String UNKNOWN_CLIENT = "unknown";

    // 실 클라이언트 IP 로 나눔(소켓 주소 · 프록시 뒤 마지막 홉이면 다같이 한 버킷)
    // 키에 라우트 ID 를 붙임. IP 만 쓰면 상한 낮은 라우트가 다른 라우트의 토큰을 깎음
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(clientIp(exchange) + ":" + routeId(exchange));
    }

    /** CF-Connecting-IP → XFF 마지막 홉 → 소켓 주소 순. 미해석 주소와 끊긴 커넥션에서 뒤의 둘은 null 이 된다. */
    private String clientIp(ServerWebExchange exchange) {
        String cloudflare = exchange.getRequest().getHeaders().getFirst(CF_CONNECTING_IP);
        if (cloudflare != null && IP_LITERAL.matcher(cloudflare.trim()).matches()) {
            return cloudflare.trim();
        }

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
