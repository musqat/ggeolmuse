package com.muscat.user.config.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.muscat.user.infra.kafka.EmailEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("인증 · 재설정 메일 발송 이벤트")
class MailServiceTest {

  private final EmailEventProducer producer = mock(EmailEventProducer.class);
  private final MailService mailService = new MailService(producer);

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(mailService, "fromEmail", "noreply@example.com");
    ReflectionTestUtils.setField(mailService, "baseUrl", "https://example.com");
  }

  private String sentContent(String type) {
    ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
    verify(producer).publishEmailSend(eq("user@example.com"), eq("noreply@example.com"), anyString(),
      content.capture(), eq(type), isNull(), isNull());
    return content.getValue();
  }

  @Test
  @DisplayName("인증 메일은 API 의 인증 주소에 토큰을 붙여 보낸다")
  void verificationEmail() {
    mailService.sendVerificationEmail("user@example.com", "verify-token");

    assertThat(sentContent("VERIFICATION")).contains("https://example.com/api/auth/verify-email?token=verify-token");
  }

  @Test
  @DisplayName("재설정 메일은 화면의 재설정 주소에 토큰을 붙여 보낸다")
  void passwordResetEmail() {
    mailService.sendPasswordResetEmail("user@example.com", "reset-token");

    assertThat(sentContent("PASSWORD_RESET")).contains("https://example.com/reset-password?token=reset-token");
  }

  @Test
  @DisplayName("이벤트 발행이 실패해도 가입 · 재설정을 막지 않게 예외를 올리지 않는다")
  void publishFails_DoesNotThrow() {
    willThrow(new IllegalStateException("broker down")).given(producer)
      .publishEmailSend(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());

    assertThatCode(() -> {
      mailService.sendVerificationEmail("user@example.com", "t");
      mailService.sendPasswordResetEmail("user@example.com", "t");
    }).doesNotThrowAnyException();
  }
}
