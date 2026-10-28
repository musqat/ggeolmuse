package com.muscat.user.infra.warmup;

import com.muscat.user.domain.user.service.KeycloakService;
import com.muscat.user.infra.kafka.LoginEventProducer;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// 기동 직후 첫 로그인의 Keycloak 토큰 발급 · Kafka 연결 비용을 미리 치르는 컴포넌트
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupWarmup {

  private final KeycloakService keycloakService;
  private final LoginEventProducer loginEventProducer;

  // 준비 상태를 늦추지 않게 다른 스레드에서
  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    CompletableFuture.runAsync(this::warmUp);
  }

  // 하나가 실패해도 나머지를 웜업(실패는 로그만)
  public void warmUp() {
    run("Keycloak 토큰", keycloakService::warmUp);
    run("로그인 이벤트 Kafka", loginEventProducer::warmUp);
  }

  private void run(String name, Runnable task) {
    long started = System.nanoTime();
    try {
      task.run();
      log.info("[웜업] {} {}ms", name, (System.nanoTime() - started) / 1_000_000);
    } catch (Exception e) {
      log.warn("[웜업] {} 실패: {}", name, e.getMessage());
    }
  }
}
