package com.muscat.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.bulkhead.autoconfigure.BulkheadAutoConfiguration;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterAutoConfiguration;
import java.net.ConnectException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JAutoConfiguration;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// 게이트웨이 경로 서킷 · 벌크헤드(값은 설정 리포 gateway-server.yml 의 resilience4j 와 같게)
class RouteCircuitBreakerTest {

  private static final String ID = "marketDataServiceCB";

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(CircuitBreakerAutoConfiguration.class,
          BulkheadAutoConfiguration.class, TimeLimiterAutoConfiguration.class,
          ReactiveResilience4JAutoConfiguration.class))
      .withPropertyValues(
          "resilience4j.timelimiter.configs.default.timeoutDuration=30s",
          "resilience4j.timelimiter.configs.default.cancelRunningFuture=false",
          "resilience4j.bulkhead.configs.default.maxConcurrentCalls=50",
          "resilience4j.bulkhead.configs.default.maxWaitDuration=0ms",
          "resilience4j.circuitbreaker.configs.default.slidingWindowSize=50",
          "resilience4j.circuitbreaker.configs.default.minimumNumberOfCalls=20",
          "resilience4j.circuitbreaker.configs.default.failureRateThreshold=50",
          "resilience4j.circuitbreaker.configs.default.waitDurationInOpenState=10s",
          "resilience4j.circuitbreaker.configs.default.automaticTransitionFromOpenToHalfOpenEnabled=true",
          "resilience4j.circuitbreaker.configs.default.permittedNumberOfCallsInHalfOpenState=5",
          "resilience4j.circuitbreaker.configs.default.ignoreExceptions[0]=io.github.resilience4j.bulkhead.BulkheadFullException");

  @Test
  @DisplayName("동시 한도를 넘는 요청이 몰려도 서킷은 닫혀 있다")
  void overloadKeepsCircuitClosed() {
    runner.run(context -> {
      ReactiveCircuitBreaker breaker = context.getBean(ReactiveCircuitBreakerFactory.class).create(ID);
      List<String> results = Flux.range(0, 3)
          .concatMap(wave -> Flux.range(0, 60)
              .flatMap(i -> breaker.run(Mono.delay(Duration.ofMillis(100)).thenReturn("ok"),
                  error -> Mono.just("fallback")), 60))
          .collectList()
          .block();

      assertThat(results).contains("ok", "fallback");
      assertThat(state(context)).isEqualTo(CircuitBreaker.State.CLOSED);
    });
  }

  @Test
  @DisplayName("하위 서비스 호출이 계속 실패하면 서킷이 열린다")
  void failuresOpenCircuit() {
    runner.run(context -> {
      ReactiveCircuitBreaker breaker = context.getBean(ReactiveCircuitBreakerFactory.class).create(ID);
      for (int i = 0; i < 20; i++) {
        breaker.run(Mono.<String>error(new ConnectException("refused")), error -> Mono.just("fallback")).block();
      }

      assertThat(state(context)).isEqualTo(CircuitBreaker.State.OPEN);
    });
  }

  private CircuitBreaker.State state(AssertableApplicationContext context) {
    return context.getBean(CircuitBreakerRegistry.class).find(ID).orElseThrow().getState();
  }
}
