package com.muscat.marketdata.infra.kafka;

import com.muscat.marketdata.domain.service.DataCollectionService;
import com.muscat.messaging.event.AssetCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

// Asset 생성 이벤트를 받아 캔들 · 배당을 수집
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
        // 추적 id 가 없으면 배치 수집
        String traceInfo = event.getTraceId() == null ? "batch" : event.getTraceId();
        log.debug("종목 생성 이벤트 수신: symbol={}, collectData={}, trace={}",
                event.getSymbol(), event.isCollectData(), traceInfo);

        try {
            // 데이터 수집이 요청되지 않은 경우 바로 ACK
            if (!event.isCollectData()) {
                log.debug("데이터 수집 요청 없음: symbol={}", event.getSymbol());
                acknowledgment.acknowledge();
                return;
            }

            // 수집이 끝난 뒤에 ACK(먼저 커밋하면 파드가 내려갈 때 안 받은 종목의 오프셋까지 넘어가 그 이벤트가 다시 안 옴)
            dataCollectionService.collect(event);

            acknowledgment.acknowledge();
            log.debug("수집 완료 후 ACK: symbol={}", event.getSymbol());

        } catch (Exception e) {
            // ACK 안 함(에러 핸들러가 재시도하고 남으면 .DLT 로)
            log.warn("종목 생성 이벤트 처리 실패: symbol={}, error={}",
                    event.getSymbol(), e.getMessage());
            throw e;
        }
    }

}
