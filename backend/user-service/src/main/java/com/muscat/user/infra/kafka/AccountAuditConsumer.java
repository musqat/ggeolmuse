package com.muscat.user.infra.kafka;

import com.muscat.messaging.event.AccountBalanceUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 계좌 감사(Audit) Consumer
 *
 * 모든 잔액 변경 이벤트를 소비하여 감사 로그로 기록합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountAuditConsumer {

    /**
     * 잔액 변경 이벤트 처리
     *
     * @param event          잔액 변경 이벤트
     * @param partition      Kafka 파티션 번호
     * @param offset         메시지 오프셋
     * @param acknowledgment 수동 커밋용 객체
     */
    @KafkaListener(
            topics = "user.account.balance.updated",
            groupId = "${spring.application.name}-balance-audit-consumer",
            containerFactory = "emailEventKafkaListenerContainerFactory" // 공통 설정 재사용
    )
    public void handleBalanceUpdated(
            @Payload AccountBalanceUpdatedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        try {
            // 감사 로그 기록
            log.info("[AUDIT] 잔액 변경: userId={}, accountId={}, updateType={}, " +
                            "KRW[{} -> {}] ({:+}), USD[{} -> {}] ({:+}), " +
                            "description={}, relatedTradeId={}, timestamp={}",
                    event.getUserId(),
                    event.getAccountId(),
                    event.getUpdateType(),
                    event.getPreviousBalanceKrw(),
                    event.getCurrentBalanceKrw(),
                    event.getKrwChange(),
                    event.getPreviousBalanceUsd(),
                    event.getCurrentBalanceUsd(),
                    event.getUsdChange(),
                    event.getDescription(),
                    event.getRelatedTradeId(),
                    event.getTimestamp());

            // 수동 커밋
            acknowledgment.acknowledge();

        } catch (Exception ex) {
            log.error("[AUDIT] 잔액 변경 이벤트 처리 실패: accountId={}, error={}",
                    event.getAccountId(), ex.getMessage(), ex);
            throw new RuntimeException("Balance update audit failed", ex);
        }
    }
}
