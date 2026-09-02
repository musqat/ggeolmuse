package com.muscat.marketdata.infra.kafka;

import com.muscat.marketdata.domain.service.DataCollectionService;
import com.muscat.messaging.event.AssetCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Asset 생성 이벤트를 처리하는 Consumer
 *
 * 비동기로 주가 및 배당 데이터를 수집합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetEventConsumer {

    private final DataCollectionService dataCollectionService;

    /**
     * Asset 생성 이벤트 처리
     */
    @KafkaListener(
            topics = "market.asset.created",
            groupId = "market-data-collection-group",
            containerFactory = "assetEventListenerFactory"
    )
    public void handleAssetCreated(AssetCreatedEvent event, Acknowledgment acknowledgment) {
        // TraceId 로깅 (BATCH_COLLECTION은 초기 수집)
        String traceInfo = "BATCH_COLLECTION".equals(event.getTraceId()) ? "batch" : event.getTraceId();
        log.info("종목 생성 이벤트 수신: symbol={}, collectData={}, trace={}",
                event.getSymbol(), event.isCollectData(), traceInfo);

        try {
            // 데이터 수집이 요청되지 않은 경우 바로 ACK
            if (!event.isCollectData()) {
                log.debug("데이터 수집 요청 없음: symbol={}", event.getSymbol());
                acknowledgment.acknowledge();
                return;
            }

            // 비동기로 데이터 수집 시작 (즉시 반환)
            // DataCollectionService의 @Async 메서드 호출 → Spring AOP proxy 정상 작동
            dataCollectionService.collectDataAsync(event);

            // 즉시 ACK하여 다음 메시지 처리 가능하게 함
            acknowledgment.acknowledge();
            log.debug("메시지 ACK 완료, 비동기 수집 시작: symbol={}", event.getSymbol());

        } catch (Exception e) {
            log.error("종목 생성 이벤트 처리 중 예상치 못한 오류: symbol={}, error={}",
                    event.getSymbol(), e.getMessage(), e);
            // 예상치 못한 오류는 재처리하지 않음
            acknowledgment.acknowledge();
        }
    }

}
