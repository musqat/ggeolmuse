package com.muscat.user.infra.kafka;

import com.muscat.messaging.event.EmailSendEvent;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 이메일 발송 이벤트를 Kafka에 발행하는 Producer
 *
 * 이메일 발송을 비동기로 처리하여 사용자 응답 속도를 개선합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailEventProducer {

    private static final String EMAIL_SEND_TOPIC = "user.email.send";

    private final KafkaTemplate<String, EmailSendEvent> kafkaTemplate;

    /**
     * 이메일 발송 이벤트 발행
     *
     * @param to 수신자 이메일
     * @param subject 이메일 제목
     * @param content 이메일 내용
     * @param emailType 이메일 타입
     */
    public void publishEmailSend(String to, String subject, String content, String emailType) {
        publishEmailSend(to, null, subject, content, emailType, null, null);
    }

    /**
     * 이메일 발송 이벤트 발행 (전체 파라미터)
     *
     * @param to 수신자 이메일
     * @param from 발신자 이메일 (null이면 기본값 사용)
     * @param subject 이메일 제목
     * @param content 이메일 내용
     * @param emailType 이메일 타입
     * @param templateVariables 템플릿 변수
     * @param userId 사용자 ID
     */
    public void publishEmailSend(String to, String from, String subject, String content,
                                  String emailType, Map<String, String> templateVariables, String userId) {
        String eventId = UUID.randomUUID().toString();

        // OpenTelemetry trace ID 추출
        String traceId = null;
        try {
            traceId = Span.current().getSpanContext().getTraceId();
        } catch (Exception e) {
            log.debug("TraceID 추출 실패: {}", e.getMessage());
        }

        EmailSendEvent event = EmailSendEvent.builder()
                .eventId(eventId)
                .eventType("EMAIL_SEND")
                .timestamp(LocalDateTime.now())
                .version("1.0")
                .traceId(traceId)
                .source("user-service")
                // Email 정보
                .to(to)
                .from(from)
                .subject(subject)
                .content(content)
                .emailType(emailType)
                .templateVariables(templateVariables)
                .userId(userId)
                .build();

        log.debug("이메일 발송 이벤트 발행 중: to={}, subject={}, emailType={}",
                to, subject, emailType);

        // 비동기로 Kafka에 전송 (to를 파티션 키로 사용)
        CompletableFuture<SendResult<String, EmailSendEvent>> future =
                kafkaTemplate.send(EMAIL_SEND_TOPIC, to, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("이메일 발송 이벤트 발행 성공: topic={}, partition={}, offset={}, to={}",
                        EMAIL_SEND_TOPIC,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        to);
            } else {
                log.error("이메일 발송 이벤트 발행 실패: to={}, error={}",
                        to, ex.getMessage(), ex);
            }
        });
    }
}
