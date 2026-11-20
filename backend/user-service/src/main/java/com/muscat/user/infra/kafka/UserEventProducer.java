package com.muscat.user.infra.kafka;

import com.muscat.messaging.event.UserDeletedEvent;
import com.muscat.messaging.kafka.EventPublishing;
import com.muscat.user.common.enums.response.UserResponse;
import com.muscat.user.common.exception.UserException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventProducer {

  private static final String DELETED_TOPIC = "user.deleted";
  private static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);

  private final KafkaTemplate<String, UserDeletedEvent> userDeletedKafkaTemplate;

  // 사용자 삭제 발행(브로커 확인 5초 대기, 못 받으면 EVENT_PUBLISH_FAILED)
  public void publishUserDeleted(String keycloakId) {
    UserDeletedEvent event = UserDeletedEvent.builder().keycloakId(keycloakId).build();
    EventPublishing.stamp(event, "USER_DELETED", "user-service");

    EventPublishing.sendAndWait(userDeletedKafkaTemplate, DELETED_TOPIC, keycloakId, event, SEND_TIMEOUT,
      e -> new UserException(UserResponse.EVENT_PUBLISH_FAILED, e));
    log.info("사용자 삭제 발행: keycloakId={}", keycloakId);
  }
}
