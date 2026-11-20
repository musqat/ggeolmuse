package com.muscat.user.domain.user.service;

import com.muscat.user.domain.user.entity.UserDeletion;
import com.muscat.user.domain.user.repository.UserDeletionRepository;
import com.muscat.user.infra.kafka.UserEventProducer;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 탈퇴한 사용자의 남은 정리(사용자 삭제 이벤트 · Keycloak 계정 삭제)를 끝날 때까지 다시 하고 끝난 기록을 지우는 컴포넌트
@Slf4j
@Component
@RequiredArgsConstructor
public class UserDeletionCleaner {

  // 끝난 기록을 두는 기간(남은 access token 15분보다 길게)
  static final Duration KEEP_AFTER_DONE = Duration.ofDays(1);

  private final UserDeletionRepository userDeletionRepository;
  private final UserEventProducer userEventProducer;
  private final KeycloakService keycloakService;
  private final Clock clock;

  // 탈퇴 트랜잭션 안에서 남기는 기록
  public void record(String keycloakId) {
    userDeletionRepository.save(UserDeletion.of(keycloakId, LocalDateTime.now(clock)));
  }

  // 탈퇴 기록이 남은 Keycloak ID 인지(끝난 뒤 하루까지)
  public boolean wasDeleted(String keycloakId) {
    return userDeletionRepository.existsById(keycloakId);
  }

  // 탈퇴 커밋 뒤 바로 한 번(실패는 로그만, 남은 단계는 다시 정리가 맡음)
  public void cleanUp(String keycloakId) {
    try {
      userDeletionRepository.findById(keycloakId).ifPresent(this::process);
    } catch (RuntimeException e) {
      log.warn("탈퇴 정리 실패 - 다음 정리에서 다시: keycloakId={}, error={}", keycloakId, e.getMessage());
    }
  }

  // 남은 단계 다시 하기 · 끝난 지 하루 지난 기록 지우기
  @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
  public void retryPending() {
    userDeletionRepository.findByEventPublishedAtIsNullOrKeycloakDeletedAtIsNull().forEach(this::process);
    long purged = userDeletionRepository.deleteByEventPublishedAtIsNotNullAndKeycloakDeletedAtBefore(
      LocalDateTime.now(clock).minus(KEEP_AFTER_DONE));
    if (purged > 0) {
      log.info("끝난 탈퇴 기록 지움: {}건", purged);
    }
  }

  private void process(UserDeletion deletion) {
    String keycloakId = deletion.getKeycloakId();
    if (deletion.getEventPublishedAt() == null) {
      try {
        userEventProducer.publishUserDeleted(keycloakId);
        deletion.markEventPublished(LocalDateTime.now(clock));
      } catch (RuntimeException e) {
        log.warn("사용자 삭제 발행 실패 - 다음 정리에서 다시: keycloakId={}, error={}", keycloakId, e.getMessage());
      }
    }
    if (deletion.getKeycloakDeletedAt() == null) {
      try {
        keycloakService.deleteUser(keycloakId);
        deletion.markKeycloakDeleted(LocalDateTime.now(clock));
      } catch (RuntimeException e) {
        log.warn("Keycloak 계정 삭제 실패 - 다음 정리에서 다시: keycloakId={}, error={}", keycloakId, e.getMessage());
      }
    }
    userDeletionRepository.save(deletion);
  }
}
