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

import com.muscat.messaging.event.EmailSendEvent;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

@DisplayName("메일 발송 발행")
class EmailEventProducerTest {

  private static final String TOPIC = "user.email.send";

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, EmailSendEvent> template = mock(KafkaTemplate.class);

  private final EmailEventProducer producer = new EmailEventProducer(template);

  private EmailSendEvent sentEvent() {
    ArgumentCaptor<EmailSendEvent> sent = ArgumentCaptor.forClass(EmailSendEvent.class);
    verify(template).send(eq(TOPIC), eq("user@example.com"), sent.capture());
    return sent.getValue();
  }

  @Test
  @DisplayName("받는 주소를 키로 보내고, 짧은 형태는 보내는 주소 · 템플릿 · 사용자를 비운다")
  void shortForm() {
    given(template.send(anyString(), anyString(), any())).willReturn(sent(TOPIC));

    producer.publishEmailSend("user@example.com", "인증 메일", "<p>본문</p>", "VERIFICATION");

    EmailSendEvent event = sentEvent();
    assertThat(event.getEventType()).isEqualTo("EMAIL_SEND");
    assertThat(event.getSubject()).isEqualTo("인증 메일");
    assertThat(event.getEmailType()).isEqualTo("VERIFICATION");
    assertThat(event.getFrom()).isNull();
    assertThat(event.getTemplateVariables()).isNull();
    assertThat(event.getUserId()).isNull();
  }

  @Test
  @DisplayName("긴 형태는 보내는 주소 · 템플릿 변수 · 사용자를 그대로 싣는다")
  void longForm() {
    given(template.send(anyString(), anyString(), any())).willReturn(sent(TOPIC));

    producer.publishEmailSend("user@example.com", "no-reply@example.com", "재설정", "본문", "PASSWORD_RESET",
      Map.of("link", "https://example.com/reset"), "7");

    EmailSendEvent event = sentEvent();
    assertThat(event.getFrom()).isEqualTo("no-reply@example.com");
    assertThat(event.getTemplateVariables()).containsEntry("link", "https://example.com/reset");
    assertThat(event.getUserId()).isEqualTo("7");
  }

  @Test
  @DisplayName("브로커가 받지 못해도 예외를 올리지 않는다")
  void sendFails_DoesNotThrow() {
    given(template.send(anyString(), anyString(), any())).willReturn(failed());

    assertThatCode(() -> producer.publishEmailSend("user@example.com", "s", "c", "VERIFICATION"))
      .doesNotThrowAnyException();
  }
}
