package com.muscat.gateway.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 타임아웃 때 게이트웨이는 원 요청 메서드 그대로 forward:/fallback 으로 넘긴다.
 * 컨트롤러가 GET 만 받으면 POST 백테스트는 503 대신 405 가 난다.
 */
@WebFluxTest(
    controllers = FallbackController.class,
    excludeAutoConfiguration = {
        ReactiveSecurityAutoConfiguration.class,
        ReactiveUserDetailsServiceAutoConfiguration.class,
        ReactiveOAuth2ResourceServerAutoConfiguration.class
    })
class FallbackControllerTest {

  @Autowired
  private WebTestClient client;

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"GET", "POST", "PUT", "DELETE", "PATCH"})
  @DisplayName("어느 메서드로 와도 503 과 안내 본문을 준다")
  void fallbackAcceptsEveryMethod(String method) {
    client.method(HttpMethod.valueOf(method))
        .uri("/fallback")
        .exchange()
        .expectStatus().isEqualTo(503)
        .expectBody()
        .jsonPath("$.status").isEqualTo(503)
        .jsonPath("$.error").isEqualTo("서비스 사용 불가")
        .jsonPath("$.timestamp").exists();
  }
}
