package com.muscat.user.infra.kafka;

import com.muscat.messaging.event.UserLoginFailedEvent;
import com.muscat.messaging.event.UserLoginSuccessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 보안 모니터링 Consumer
 *
 * 로그인 성공/실패 이벤트를 소비하여 보안 로그로 기록합니다.
 * 향후 확장:
 * - 실시간 이상 로그인 탐지 (같은 IP에서 짧은 시간에 여러 계정 로그인)
 * - 로그인 실패 횟수 추적 (계정 잠금 정책)
 * - 지역별 로그인 분석 (평소와 다른 국가에서 로그인)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityMonitoringConsumer {

    /**
     * 로그인 성공 이벤트 처리
     *
     * @param event          로그인 성공 이벤트
     * @param partition      Kafka 파티션 번호
     * @param offset         메시지 오프셋
     * @param acknowledgment 수동 커밋용 객체
     */
    @KafkaListener(
            topics = "user.login.success",
            groupId = "${spring.application.name}-login-success-consumer",
            containerFactory = "emailEventKafkaListenerContainerFactory" // 공통 설정 재사용
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
            throw new RuntimeException("Login success event processing failed", ex);
        }
    }

    /**
     * 로그인 실패 이벤트 처리
     *
     * @param event          로그인 실패 이벤트
     * @param partition      Kafka 파티션 번호
     * @param offset         메시지 오프셋
     * @param acknowledgment 수동 커밋용 객체
     */
    @KafkaListener(
            topics = "user.login.failed",
            groupId = "${spring.application.name}-login-failed-consumer",
            containerFactory = "emailEventKafkaListenerContainerFactory" // 공통 설정 재사용
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
            throw new RuntimeException("Login failed event processing failed", ex);
        }
    }
}
