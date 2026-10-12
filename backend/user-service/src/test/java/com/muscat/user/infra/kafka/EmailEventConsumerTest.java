package com.muscat.user.infra.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.EmailSendEvent;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("메일 발송 소비")
class EmailEventConsumerTest {

  private final JavaMailSender mailSender = mock(JavaMailSender.class);
  private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
  private final EmailEventConsumer consumer = new EmailEventConsumer(mailSender);

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(consumer, "defaultFromEmail", "noreply@example.com");
    given(mailSender.createMimeMessage()).willReturn(new MimeMessage(Session.getInstance(new Properties())));
  }

  private static EmailSendEvent event(String from) {
    return EmailSendEvent.builder().to("user@example.com").from(from).subject("인증 메일")
      .content("<p>본문</p>").emailType("VERIFICATION").build();
  }

  private MimeMessage sentMessage() {
    ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(sent.capture());
    return sent.getValue();
  }

  @Test
  @DisplayName("보내는 주소가 없으면 기본 주소로 보내고 커밋한다")
  void defaultFrom() throws Exception {
    consumer.handleEmailSend(event(null), 0, 1L, acknowledgment);

    MimeMessage message = sentMessage();
    assertThat(((InternetAddress) message.getFrom()[0]).getAddress()).isEqualTo("noreply@example.com");
    assertThat(message.getAllRecipients()[0].toString()).isEqualTo("user@example.com");
    assertThat(message.getSubject()).isEqualTo("인증 메일");
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("이벤트의 보내는 주소가 있으면 그 주소로 보낸다")
  void eventFrom() throws Exception {
    consumer.handleEmailSend(event("support@example.com"), 0, 1L, acknowledgment);

    assertThat(((InternetAddress) sentMessage().getFrom()[0]).getAddress()).isEqualTo("support@example.com");
  }

  @Test
  @DisplayName("메일 서버가 실패하면 커밋하지 않고 예외를 올려 다시 받게 한다")
  void sendFails_DoesNotAcknowledge() {
    willThrow(new MailSendException("smtp down")).given(mailSender).send(any(MimeMessage.class));

    assertThatThrownBy(() -> consumer.handleEmailSend(event(null), 0, 1L, acknowledgment))
      .isInstanceOf(RuntimeException.class)
      .hasMessage("Email send failed");
    verify(acknowledgment, never()).acknowledge();
  }
}
