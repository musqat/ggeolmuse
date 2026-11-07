package com.muscat.trade.domain.service;

import com.muscat.trade.domain.repository.IdempotencyRecordRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 하루 지난 중복 요청 키 지우기(한 시간마다, 파드 하나만)
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyKeyCleaner {

  public static final String LOCK_NAME = "idempotency-key-cleanup";

  // 같은 키로 다시 보내 처음 체결을 받을 수 있는 시간
  static final Duration RETENTION = Duration.ofDays(1);

  private final IdempotencyRecordRepository repository;

  @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT10M")
  @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "10m", lockAtLeastFor = "1m")
  @Transactional
  public void deleteExpired() {
    long deleted = repository.deleteByCreatedAtBefore(LocalDateTime.now().minus(RETENTION));
    // 지운 행의 DELETE 를 로그 전에 실행
    repository.flush();
    if (deleted > 0) {
      log.info("하루 지난 중복 요청 키 지움: {}건", deleted);
    }
  }
}
