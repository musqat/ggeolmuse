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

// 타임아웃 때 원래 메서드 그대로 forward:/fallback 으로 옴(GET 만 받으면 POST 는 503 대신 405)
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
  @DisplayName("어느 메서드로 와도 503 과 오류 코드 · 안내 문구가 든 ProblemDetail 을 준다")
  void fallbackAcceptsEveryMethod(String method) {
    client.method(HttpMethod.valueOf(method))
      .uri("/fallback")
      .exchange()
      .expectStatus().isEqualTo(503)
      .expectBody()
      .jsonPath("$.status").isEqualTo(503)
      .jsonPath("$.title").isEqualTo("Service Unavailable")
      .jsonPath("$.errorCode").isEqualTo("SERVICE_UNAVAILABLE")
      .jsonPath("$.errorType").isEqualTo("EXTERNAL_SERVICE")
      .jsonPath("$.detail").isEqualTo("요청하신 서비스를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해주세요.")
      .jsonPath("$.instance").isEqualTo("/fallback")
      .jsonPath("$.timestamp").exists();
  }
}
