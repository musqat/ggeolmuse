package com.muscat.user.infra.kafka;

import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.user.domain.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Trade 이벤트를 Kafka에서 소비하는 Consumer
 *
 * trade-service에서 발행한 TradeCompletedEvent를 소비하여
 * 사용자 계좌 잔액을 비동기로 업데이트합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeEventConsumer {

    private final AccountService accountService;

    /**
     * 거래 완료 이벤트 처리
     *
     * @param event          거래 완료 이벤트
     * @param partition      Kafka 파티션 번호
     * @param offset         메시지 오프셋
     * @param acknowledgment 수동 커밋용 객체
     */
    @KafkaListener(
            topics = "trading.trade.completed",
            groupId = "${spring.application.name}-trade-consumer",
            containerFactory = "tradeEventKafkaListenerContainerFactory"
    )
    public void handleTradeCompleted(
            @Payload TradeCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        log.info("거래 완료 이벤트 수신: tradeId={}, userId={}, symbol={}, amount={}, partition={}, offset={}",
                event.getTradeId(), event.getUserId(), event.getSymbol(),
                event.getTotalAmount(), partition, offset);

        try {
            // 계좌 잔액 업데이트
            accountService.processTradeEvent(event);

            // 수동 커밋 (처리 성공시에만)
            acknowledgment.acknowledge();

            log.info("거래 이벤트 처리 완료: tradeId={}, userId={}",
                    event.getTradeId(), event.getUserId());

        } catch (Exception ex) {
            log.error("거래 이벤트 처리 실패: tradeId={}, userId={}, error={}",
                    event.getTradeId(), event.getUserId(), ex.getMessage(), ex);

            // 재시도 실패시 DLQ (Dead Letter Queue)로 이동
            throw ex;
        }
    }
}
