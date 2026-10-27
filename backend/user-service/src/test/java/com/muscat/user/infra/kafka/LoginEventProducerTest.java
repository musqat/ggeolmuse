package com.muscat.user.infra.kafka;

import static com.muscat.user.infra.kafka.KafkaSends.failed;
import static com.muscat.user.infra.kafka.KafkaSends.sent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.UserLoginFailedEvent;
import com.muscat.messaging.event.UserLoginSuccessEvent;
import com.muscat.user.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

@DisplayName("로그인 성공 · 실패 발행")
class LoginEventProducerTest {

  private static final String SUCCESS_TOPIC = "user.login.success";
  private static final String FAILED_TOPIC = "user.login.failed";

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, UserLoginSuccessEvent> successTemplate = mock(KafkaTemplate.class);

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, UserLoginFailedEvent> failedTemplate = mock(KafkaTemplate.class);

  private final LoginEventProducer producer = new LoginEventProducer(successTemplate, failedTemplate);

  @Test
  @DisplayName("로그인 성공은 이메일을 키로 사용자 · 접속 정보를 보낸다")
  void loginSuccess() {
    given(successTemplate.send(anyString(), anyString(), any())).willReturn(sent(SUCCESS_TOPIC));
    User user = User.builder().id(7L).email("user@example.com").nickname("투자자").build();

    producer.publishLoginSuccess(user, "PASSWORD", "203.0.113.5", "Mozilla/5.0", true);

    ArgumentCaptor<UserLoginSuccessEvent> sent = ArgumentCaptor.forClass(UserLoginSuccessEvent.class);
    verify(successTemplate).send(eq(SUCCESS_TOPIC), eq("user@example.com"), sent.capture());
    assertThat(sent.getValue().getEventType()).isEqualTo("USER_LOGIN_SUCCESS");
    assertThat(sent.getValue().getUserId()).isEqualTo("7");
    assertThat(sent.getValue().getNickname()).isEqualTo("투자자");
    assertThat(sent.getValue().getIpAddress()).isEqualTo("203.0.113.5");
    assertThat(sent.getValue().getTokenIssued()).isTrue();
  }

  @Test
  @DisplayName("데우기는 로그인 성공 토픽의 메타데이터를 받아 연결을 열어 둔다")
  void warmUp() {
    producer.warmUp();

    verify(successTemplate).partitionsFor(SUCCESS_TOPIC);
  }

  @Test
  @DisplayName("로그인 실패는 이메일을 키로 실패 사유를 보낸다(보안 감시가 읽는다)")
  void loginFailed() {
    given(failedTemplate.send(anyString(), anyString(), any())).willReturn(sent(FAILED_TOPIC));

    producer.publishLoginFailed("user@example.com", "INVALID_PASSWORD", "비밀번호 불일치", "PASSWORD",
      "203.0.113.5", "Mozilla/5.0");

    ArgumentCaptor<UserLoginFailedEvent> sent = ArgumentCaptor.forClass(UserLoginFailedEvent.class);
    verify(failedTemplate).send(eq(FAILED_TOPIC), eq("user@example.com"), sent.capture());
    assertThat(sent.getValue().getEventType()).isEqualTo("USER_LOGIN_FAILED");
    assertThat(sent.getValue().getFailureReason()).isEqualTo("INVALID_PASSWORD");
    assertThat(sent.getValue().getIpAddress()).isEqualTo("203.0.113.5");
  }

  @Test
  @DisplayName("브로커가 받지 못해도 로그인 응답을 막지 않게 예외를 올리지 않는다")
  void sendFails_DoesNotThrow() {
    given(successTemplate.send(anyString(), anyString(), any())).willReturn(failed());
    given(failedTemplate.send(anyString(), anyString(), any())).willReturn(failed());

    assertThatCode(() -> {
      producer.publishLoginSuccess(User.builder().id(7L).email("user@example.com").build(), "PASSWORD", null, null, true);
      producer.publishLoginFailed("user@example.com", "INVALID_PASSWORD", null, "PASSWORD", null, null);
    }).doesNotThrowAnyException();
  }
}
