package com.muscat.user.infra.kafka;

import com.muscat.messaging.event.UserLoginFailedEvent;
import com.muscat.messaging.event.UserLoginSuccessEvent;
import com.muscat.user.domain.user.entity.User;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 로그인 관련 이벤트를 Kafka에 발행하는 Producer
 *
 * 사용자 로그인 성공/실패 이벤트를 발행하여
 * 보안 모니터링, 사용자 활동 추적, 이상 로그인 탐지 등에 활용할 수 있도록 합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginEventProducer {

    private static final String LOGIN_SUCCESS_TOPIC = "user.login.success";
    private static final String LOGIN_FAILED_TOPIC = "user.login.failed";

    private final KafkaTemplate<String, UserLoginSuccessEvent> loginSuccessKafkaTemplate;
    private final KafkaTemplate<String, UserLoginFailedEvent> loginFailedKafkaTemplate;

    /**
     * 로그인 성공 이벤트 발행
     *
     * @param user 로그인한 사용자
     * @param loginMethod 로그인 방식 ("PASSWORD", "GOOGLE_OAUTH")
     * @param ipAddress 클라이언트 IP 주소
     * @param userAgent User Agent 정보
     * @param tokenIssued 토큰 발급 여부
     */
    public void publishLoginSuccess(User user, String loginMethod, String ipAddress,
                                      String userAgent, Boolean tokenIssued) {
        String eventId = UUID.randomUUID().toString();

        // OpenTelemetry trace ID 추출
        String traceId = null;
        try {
            traceId = Span.current().getSpanContext().getTraceId();
        } catch (Exception e) {
            log.debug("TraceID 추출 실패: {}", e.getMessage());
        }

        UserLoginSuccessEvent event = UserLoginSuccessEvent.builder()
                .eventId(eventId)
                .eventType("USER_LOGIN_SUCCESS")
                .timestamp(LocalDateTime.now())
                .version("1.0")
                .traceId(traceId)
                .source("user-service")
                // Login 정보
                .userId(String.valueOf(user.getId()))
                .email(user.getEmail())
                .nickname(user.getNickname())
                .loginMethod(loginMethod)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .tokenIssued(tokenIssued)
                .build();

        log.info("로그인 성공 이벤트 발행 중: userId={}, email={}, loginMethod={}",
                user.getId(), user.getEmail(), loginMethod);

        // 비동기로 Kafka에 전송
        CompletableFuture<SendResult<String, UserLoginSuccessEvent>> future =
                loginSuccessKafkaTemplate.send(LOGIN_SUCCESS_TOPIC, user.getEmail(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("로그인 성공 이벤트 발행 성공: topic={}, partition={}, offset={}, userId={}, email={}",
                        LOGIN_SUCCESS_TOPIC,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        user.getId(), user.getEmail());
            } else {
                log.error("로그인 성공 이벤트 발행 실패: userId={}, email={}, error={}",
                        user.getId(), user.getEmail(), ex.getMessage(), ex);
            }
        });
    }

    /**
     * 로그인 실패 이벤트 발행
     *
     * @param email 로그인 시도한 이메일
     * @param failureReason 실패 사유 코드
     * @param failureMessage 실패 상세 메시지
     * @param loginMethod 로그인 방식 ("PASSWORD", "GOOGLE_OAUTH")
     * @param ipAddress 클라이언트 IP 주소
     * @param userAgent User Agent 정보
     */
    public void publishLoginFailed(String email, String failureReason, String failureMessage,
                                     String loginMethod, String ipAddress, String userAgent) {
        String eventId = UUID.randomUUID().toString();

        // OpenTelemetry trace ID 추출
        String traceId = null;
        try {
            traceId = Span.current().getSpanContext().getTraceId();
        } catch (Exception e) {
            log.debug("TraceID 추출 실패: {}", e.getMessage());
        }

        UserLoginFailedEvent event = UserLoginFailedEvent.builder()
                .eventId(eventId)
                .eventType("USER_LOGIN_FAILED")
                .timestamp(LocalDateTime.now())
                .version("1.0")
                .traceId(traceId)
                .source("user-service")
                // Login 실패 정보
                .email(email)
                .failureReason(failureReason)
                .failureMessage(failureMessage)
                .loginMethod(loginMethod)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        log.info("로그인 실패 이벤트 발행 중: email={}, failureReason={}, loginMethod={}",
                email, failureReason, loginMethod);

        // 비동기로 Kafka에 전송
        CompletableFuture<SendResult<String, UserLoginFailedEvent>> future =
                loginFailedKafkaTemplate.send(LOGIN_FAILED_TOPIC, email, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("로그인 실패 이벤트 발행 성공: topic={}, partition={}, offset={}, email={}, failureReason={}",
                        LOGIN_FAILED_TOPIC,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        email, failureReason);
            } else {
                log.error("로그인 실패 이벤트 발행 실패: email={}, failureReason={}, error={}",
                        email, failureReason, ex.getMessage(), ex);
            }
        });
    }
}
