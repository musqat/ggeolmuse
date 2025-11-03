package com.muscat.trade.infra.kafka;

import com.muscat.messaging.event.PriceUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 주가 업데이트 이벤트를 Kafka에서 소비하는 Consumer
 *
 * market-data-service에서 발행한 PriceUpdatedEvent를 소비하여
 * 실시간 주가 정보를 활용합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceEventConsumer {

    /**
     * 주가 업데이트 이벤트 처리
     *
     * @param event          주가 업데이트 이벤트
     * @param partition      Kafka 파티션 번호
     * @param offset         메시지 오프셋
     * @param acknowledgment 수동 커밋용 객체
     */
    @KafkaListener(
            topics = "market.price.updated",
            groupId = "${spring.application.name}-price-consumer",
            containerFactory = "priceEventKafkaListenerContainerFactory"
    )
    public void handlePriceUpdated(
            @Payload PriceUpdatedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        log.debug("주가 업데이트 이벤트 수신: symbol={}, date={}, adjustedClose={}, partition={}, offset={}",
                event.getSymbol(), event.getDate(), event.getAdjustedClose(), partition, offset);

        try {
            processPriceUpdate(event);

            // 수동 커밋 (처리 성공시에만)
            acknowledgment.acknowledge();

            log.trace("주가 이벤트 처리 완료: symbol={}, date={}",
                    event.getSymbol(), event.getDate());

        } catch (Exception ex) {
            log.error("주가 이벤트 처리 실패: symbol={}, date={}, error={}",
                    event.getSymbol(), event.getDate(), ex.getMessage(), ex);

            // NOTE: 수동 커밋하지 않음 -> Kafka가 재시도
            throw ex;
        }
    }

    /**
     * 주가 업데이트 처리 로직
     *
     * @param event 주가 업데이트 이벤트
     */
    private void processPriceUpdate(PriceUpdatedEvent event) {
        // 현재는 로그만 기록 (향후 캐시 업데이트, 포트폴리오 평가액 갱신 등 추가 가능)
        log.info("주가 업데이트: symbol={}, date={}, open={}, high={}, low={}, close={}, adjustedClose={}, volume={}",
                event.getSymbol(),
                event.getDate(),
                event.getOpen(),
                event.getHigh(),
                event.getLow(),
                event.getClose(),
                event.getAdjustedClose(),
                event.getVolume());
    }
}
