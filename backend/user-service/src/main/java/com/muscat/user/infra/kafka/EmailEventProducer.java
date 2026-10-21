package com.muscat.user.infra.kafka;

import com.muscat.messaging.event.EmailSendEvent;
import com.muscat.messaging.kafka.EventPublishing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

// 이메일 발송 이벤트 발행. 발송은 컨슈머가 비동기로
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailEventProducer {

    private static final String EMAIL_SEND_TOPIC = "user.email.send";

    private final KafkaTemplate<String, EmailSendEvent> kafkaTemplate;

    public void publishEmailSend(String to, String subject, String content, String emailType) {
        publishEmailSend(to, null, subject, content, emailType, null, null);
    }

    // from 이 비면 기본 발신자
    public void publishEmailSend(String to, String from, String subject, String content,
                                  String emailType, Map<String, String> templateVariables, String userId) {
        EmailSendEvent event = EmailSendEvent.builder()
                .to(to)
                .from(from)
                .subject(subject)
                .content(content)
                .emailType(emailType)
                .templateVariables(templateVariables)
                .userId(userId)
                .build();
        EventPublishing.stamp(event, "EMAIL_SEND", "user-service");

        log.debug("이메일 발송 이벤트 발행 중: to={}, subject={}, emailType={}",
                to, subject, emailType);

        EventPublishing.send(kafkaTemplate, EMAIL_SEND_TOPIC, to, event);
    }
}
