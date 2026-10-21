package com.muscat.user.infra.kafka;

import com.muscat.messaging.event.UserLoginFailedEvent;
import com.muscat.messaging.event.UserLoginSuccessEvent;
import com.muscat.user.common.enums.response.UserResponse;
import com.muscat.user.common.exception.UserException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

// 보안 모니터링: 로그인 성공 · 실패 이벤트를 보안 로그로 남김
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityMonitoringConsumer {

    @KafkaListener(
            topics = "user.login.success",
            groupId = "${spring.application.name}-login-success-consumer",
            containerFactory = "loginSuccessEventKafkaListenerContainerFactory"
    )
    public void handleLoginSuccess(
            @Payload UserLoginSuccessEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        try {
            // 보안 로그 기록
            log.info("[SECURITY] 로그인 성공: userId={}, email={}, loginMethod={}, ipAddress={}, userAgent={}, timestamp={}",
                    event.getUserId(),
                    event.getEmail(),
                    event.getLoginMethod(),
                    event.getIpAddress(),
                    event.getUserAgent(),
                    event.getTimestamp());

            // 수동 커밋
            acknowledgment.acknowledge();

        } catch (Exception ex) {
            log.error("[SECURITY] 로그인 성공 이벤트 처리 실패: email={}, error={}",
                    event.getEmail(), ex.getMessage(), ex);
            throw new UserException(UserResponse.EVENT_PROCESSING_FAILED, ex);
        }
    }

    @KafkaListener(
            topics = "user.login.failed",
            groupId = "${spring.application.name}-login-failed-consumer",
            containerFactory = "loginFailedEventKafkaListenerContainerFactory"
    )
    public void handleLoginFailed(
            @Payload UserLoginFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        try {
            // 보안 경고 로그 기록
            log.warn("[SECURITY] 로그인 실패: email={}, failureReason={}, failureMessage={}, loginMethod={}, ipAddress={}, userAgent={}, timestamp={}",
                    event.getEmail(),
                    event.getFailureReason(),
                    event.getFailureMessage(),
                    event.getLoginMethod(),
                    event.getIpAddress(),
                    event.getUserAgent(),
                    event.getTimestamp());

            // 수동 커밋
            acknowledgment.acknowledge();

        } catch (Exception ex) {
            log.error("[SECURITY] 로그인 실패 이벤트 처리 실패: email={}, error={}",
                    event.getEmail(), ex.getMessage(), ex);
            throw new UserException(UserResponse.EVENT_PROCESSING_FAILED, ex);
        }
    }
}
