package com.muscat.gateway.config;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

// 기동 직후 첫 요청의 Redis 레이트 리밋 · 백엔드 연결 비용을 미리 치르는 컴포넌트
@Component
public class GatewayWarmup {

    private static final Logger log = LoggerFactory.getLogger(GatewayWarmup.class);

    /** 레이트 리밋 웜업 키(실 클라이언트 버킷과 겹치지 않음) */
    private static final String WARMUP_KEY = "warmup";

    /** 첫 Redis 연결 · 스크립트 적재까지 기다리는 시간 */
    private static final Duration RATE_LIMITER_TIMEOUT = Duration.ofSeconds(15);

    private static final Duration BACKEND_TIMEOUT = Duration.ofSeconds(5);

    private final RouteLocator routeLocator;
    private final RedisRateLimiter rateLimiter;
    private final HttpClient httpClient;

    public GatewayWarmup(RouteLocator routeLocator, RedisRateLimiter rateLimiter, HttpClient httpClient) {
        this.routeLocator = routeLocator;
        this.rateLimiter = rateLimiter;
        this.httpClient = httpClient;
    }

    // 준비 상태를 늦추지 않게 구독만 하고 기다리지 않음
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        warmUp().subscribe();
    }

    // 라우트를 읽은 뒤 레이트 리밋과 http 백엔드마다 연결 하나(실패는 로그만)
    public Mono<Void> warmUp() {
        return routeLocator.getRoutes()
            .map(Route::getUri)
            .filter(uri -> "http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
            .distinct()
            .collectList()
            .flatMap(backends -> Mono.when(warmRateLimiter(), warmBackends(backends)))
            .onErrorResume(e -> {
                log.warn("[웜업] 라우트 읽기 실패: {}", e.toString());
                return Mono.empty();
            });
    }

    private Mono<Void> warmRateLimiter() {
        return Mono.defer(() -> rateLimiter.getConfig().keySet().stream().findFirst()
            .map(routeId -> timed("레이트 리밋", rateLimiter.isAllowed(routeId, WARMUP_KEY), RATE_LIMITER_TIMEOUT))
            .orElseGet(Mono::empty));
    }

    private Mono<Void> warmBackends(List<URI> backends) {
        return Flux.fromIterable(backends)
            .flatMap(uri -> timed("백엔드 " + uri.getAuthority(),
                httpClient.responseTimeout(BACKEND_TIMEOUT).get().uri(uri).response(), BACKEND_TIMEOUT))
            .then();
    }

    private Mono<Void> timed(String name, Mono<?> task, Duration timeout) {
        return Mono.defer(() -> {
            long started = System.nanoTime();
            return task.timeout(timeout)
                .doOnSuccess(ignored -> log.info("[웜업] {} {}ms", name, (System.nanoTime() - started) / 1_000_000))
                .onErrorResume(e -> {
                    log.warn("[웜업] {} 실패: {}", name, e.toString());
                    return Mono.empty();
                })
                .then();
        });
    }
}
