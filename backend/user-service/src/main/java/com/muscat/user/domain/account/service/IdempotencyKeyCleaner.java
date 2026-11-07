package com.muscat.user.domain.account.service;

import com.muscat.user.domain.account.repository.IdempotencyRecordRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 하루 지난 중복 요청 키 지우기(한 시간마다)
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyKeyCleaner {

  // 같은 키로 다시 보내 처음 결과를 받을 수 있는 시간
  static final Duration RETENTION = Duration.ofDays(1);

  private final IdempotencyRecordRepository repository;

  @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT10M")
  @Transactional
  public void deleteExpired() {
    long deleted = repository.deleteByCreatedAtBefore(LocalDateTime.now().minus(RETENTION));
    // 지운 행의 DELETE 를 로그 전에 실행(다른 파드가 먼저 지웠으면 여기서 예외)
    repository.flush();
    if (deleted > 0) {
      log.info("하루 지난 중복 요청 키 지움: {}건", deleted);
    }
  }
}
