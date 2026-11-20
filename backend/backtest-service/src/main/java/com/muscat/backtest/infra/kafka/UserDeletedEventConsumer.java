package com.muscat.backtest.infra.kafka;

import com.muscat.backtest.domain.repository.BacktestHistoryRepository;
import com.muscat.messaging.event.UserDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

// 사용자 삭제 이벤트를 받아 그 사용자의 백테스트 이력을 지우는 컨슈머
@Slf4j
@Component
@RequiredArgsConstructor
public class UserDeletedEventConsumer {

  private final BacktestHistoryRepository backtestHistoryRepository;

  @KafkaListener(
    topics = "user.deleted",
    groupId = "${spring.application.name}-user-deleted-consumer",
    containerFactory = "userDeletedEventKafkaListenerContainerFactory",
    autoStartup = "${backtest.kafka.auto-startup:true}"
  )
  public void handleUserDeleted(@Payload UserDeletedEvent event, Acknowledgment acknowledgment) {
    String keycloakId = event.getKeycloakId();
    // 빈 ID 로 지우면 주인 없는(비로그인) 이력까지 걸림
    if (keycloakId == null || keycloakId.isBlank()) {
      log.warn("Keycloak ID 없는 사용자 삭제 이벤트 - 건너뜀: eventId={}", event.getEventId());
      acknowledgment.acknowledge();
      return;
    }
    long deleted = backtestHistoryRepository.deleteByUserId(keycloakId);
    log.info("사용자 삭제로 백테스트 이력 지움: keycloakId={}, 건수={}", keycloakId, deleted);
    acknowledgment.acknowledge();
  }
}
