package com.muscat.user.infra.kafka;

import static com.muscat.user.infra.kafka.KafkaSends.failed;
import static com.muscat.user.infra.kafka.KafkaSends.sent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.UserDeletedEvent;
import com.muscat.user.common.exception.UserException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

@DisplayName("사용자 삭제 발행")
class UserEventProducerTest {

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, UserDeletedEvent> template = mock(KafkaTemplate.class);

  private final UserEventProducer producer = new UserEventProducer(template);

  @Test
  @DisplayName("Keycloak ID 를 키 · 본문으로 user.deleted 에 보낸다")
  void publishUserDeleted_Sends() {
    given(template.send(anyString(), anyString(), any())).willReturn(sent("user.deleted"));

    producer.publishUserDeleted("kc-1");

    ArgumentCaptor<UserDeletedEvent> event = ArgumentCaptor.forClass(UserDeletedEvent.class);
    verify(template).send(eq("user.deleted"), eq("kc-1"), event.capture());
    assertThat(event.getValue().getKeycloakId()).isEqualTo("kc-1");
    assertThat(event.getValue().getEventType()).isEqualTo("USER_DELETED");
  }

  @Test
  @DisplayName("브로커가 받지 못하면 이벤트 발행 실패 예외가 난다")
  void publishUserDeleted_SendFails_Throws() {
    given(template.send(anyString(), anyString(), any())).willReturn(failed());

    assertThatThrownBy(() -> producer.publishUserDeleted("kc-1"))
      .isInstanceOf(UserException.class)
      .hasFieldOrPropertyWithValue("errorCode", "EVENT_PUBLISH_FAILED");
  }
}
