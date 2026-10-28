package com.muscat.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

// 백엔드는 이 테스트가 띄운 HTTP 서버, fallback 처럼 http 가 아닌 경로는 건너뜀
class GatewayWarmupTest {

  private static final String ROUTE = "user-login-route";

  private HttpServer backend;
  private final AtomicInteger hits = new AtomicInteger();
  private final RedisRateLimiter rateLimiter = mock(RedisRateLimiter.class);
  private GatewayWarmup warmup;

  @BeforeEach
  void setUp() throws IOException {
    backend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    backend.createContext("/", exchange -> {
      hits.incrementAndGet();
      exchange.sendResponseHeaders(404, -1);
      exchange.close();
    });
    backend.start();
    URI uri = URI.create("http://127.0.0.1:" + backend.getAddress().getPort());
    RouteLocator routes = () -> Flux.just(route(ROUTE, uri), route("user-auth-route", uri),
        route("fallback", URI.create("forward:/fallback")));
    given(rateLimiter.getConfig()).willReturn(Map.of(ROUTE, new RedisRateLimiter.Config()));
    warmup = new GatewayWarmup(routes, rateLimiter, HttpClient.create());
  }

  @AfterEach
  void tearDown() {
    backend.stop(0);
  }

  @Test
  @DisplayName("레이트 리밋을 한 번 돌리고 백엔드마다 연결을 하나씩 맺는다")
  void warmsRateLimiterAndBackends() {
    given(rateLimiter.isAllowed(ROUTE, "warmup")).willReturn(Mono.just(new RateLimiter.Response(true, Map.of())));

    warmup.warmUp().block(Duration.ofSeconds(10));

    verify(rateLimiter).isAllowed(ROUTE, "warmup");
    assertThat(hits.get()).as("같은 백엔드는 한 번").isEqualTo(1);
  }

  @Test
  @DisplayName("레이트 리밋 웜업이 실패해도 백엔드는 웜업하고 예외를 내지 않는다")
  void rateLimiterFailureIsLoggedOnly() {
    given(rateLimiter.isAllowed(ROUTE, "warmup")).willReturn(Mono.error(new IllegalStateException("Redis 안 뜸")));

    assertThatCode(() -> warmup.warmUp().block(Duration.ofSeconds(10))).doesNotThrowAnyException();
    assertThat(hits.get()).isEqualTo(1);
  }

  private static Route route(String id, URI uri) {
    return Route.async().id(id).uri(uri).predicate(exchange -> true).build();
  }
}
