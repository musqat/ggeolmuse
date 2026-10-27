package com.muscat.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

// 운영은 Cloudflare → k3s 로드밸런서 → Traefik → 게이트웨이라 XFF 마지막 홉과 소켓 주소가 클러스터 내부 주소가 됨
class RateLimiterConfigTest {

  private static final String ROUTE = "user-login-route";
  private static final String INTERNAL = "10.42.0.5";

  private final RateLimiterConfig config = new RateLimiterConfig();

  @Test
  @DisplayName("Cloudflare 가 넣은 CF-Connecting-IP 로 버킷을 나눈다")
  void cloudflareClientIp() {
    String key = keyOf(MockServerHttpRequest.get("/api/auth/login")
        .header("CF-Connecting-IP", "203.0.113.7")
        .header("X-Forwarded-For", INTERNAL));

    assertThat(key).isEqualTo("203.0.113.7:" + ROUTE);
  }

  @Test
  @DisplayName("CF-Connecting-IP 는 IPv6 도 그대로 쓴다")
  void cloudflareIpv6() {
    String key = keyOf(MockServerHttpRequest.get("/api/auth/login")
        .header("CF-Connecting-IP", "2001:db8::1")
        .header("X-Forwarded-For", INTERNAL));

    assertThat(key).isEqualTo("2001:db8::1:" + ROUTE);
  }

  @Test
  @DisplayName("CF-Connecting-IP 가 IP 모양이 아니면 버리고 XFF 마지막 홉을 쓴다")
  void malformedCloudflareIp() {
    String key = keyOf(MockServerHttpRequest.get("/api/auth/login")
        .header("CF-Connecting-IP", "evil value")
        .header("X-Forwarded-For", "198.51.100.1"));

    assertThat(key).isEqualTo("198.51.100.1:" + ROUTE);
  }

  @Test
  @DisplayName("Cloudflare 를 거치지 않으면 지금처럼 XFF 마지막 홉으로 나눈다")
  void forwardedForWithoutCloudflare() {
    String key = keyOf(MockServerHttpRequest.get("/api/auth/login")
        .header("X-Forwarded-For", "198.51.100.1"));

    assertThat(key).isEqualTo("198.51.100.1:" + ROUTE);
  }

  @Test
  @DisplayName("헤더가 둘 다 없으면 소켓 주소로 나눈다")
  void socketAddress() {
    String key = keyOf(MockServerHttpRequest.get("/api/auth/login"));

    assertThat(key).isEqualTo("192.0.2.3:" + ROUTE);
  }

  private String keyOf(MockServerHttpRequest.BaseBuilder<?> request) {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        request.remoteAddress(new InetSocketAddress("192.0.2.3", 40000)).build());
    exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
        Route.async().id(ROUTE).uri(URI.create("http://user-service:8080")).predicate(e -> true).build());
    return config.ipKeyResolver().resolve(exchange).block();
  }
}
