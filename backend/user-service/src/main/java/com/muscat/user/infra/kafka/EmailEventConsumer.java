package com.muscat.user.infra.kafka;

import com.muscat.messaging.event.EmailSendEvent;
import com.muscat.user.common.enums.response.UserResponse;
import com.muscat.user.common.exception.UserException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * EmailSendEvent 를 받아 이메일을 보내는 Kafka 컨슈머
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailEventConsumer {

  private final JavaMailSender mailSender;

  @Value("${app.mail.from:noreply@example.com}")
  private String defaultFromEmail;

  @KafkaListener(
    topics = "user.email.send",
    groupId = "${spring.application.name}-email-consumer",
    containerFactory = "emailEventKafkaListenerContainerFactory"
  )
  public void handleEmailSend(
    @Payload EmailSendEvent event,
    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
    @Header(KafkaHeaders.OFFSET) long offset,
    Acknowledgment acknowledgment
  ) {
    log.info("이메일 발송 이벤트 수신: to={}, subject={}, emailType={}, partition={}, offset={}",
      event.getTo(), event.getSubject(), event.getEmailType(), partition, offset);

    try {
      // 이메일 발송
      sendEmail(event);

      // 수동 커밋 (처리 성공시에만)
      acknowledgment.acknowledge();

      log.info("이메일 발송 완료: to={}, subject={}",
        event.getTo(), event.getSubject());

    } catch (Exception ex) {
      log.error("이메일 발송 실패: to={}, subject={}, error={}",
        event.getTo(), event.getSubject(), ex.getMessage(), ex);

      // NOTE: 수동 커밋하지 않음 -> Kafka가 재시도
      // 재시도 실패시 DLQ로 이동
      throw new UserException(UserResponse.EMAIL_SEND_FAILED, ex);
    }
  }

  private void sendEmail(EmailSendEvent event) throws MessagingException, UnsupportedEncodingException {
    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

    String fromAddress = event.getFrom() != null ? event.getFrom() : defaultFromEmail;
    helper.setFrom(fromAddress, "GGeolmuse");
    helper.setTo(event.getTo());
    helper.setSubject(event.getSubject());
    helper.setText(event.getContent(), true); // HTML 지원

    mailSender.send(message);

    log.debug("이메일 발송 성공: to={}, from={}, subject={}",
      event.getTo(), fromAddress, event.getSubject());
  }
}
