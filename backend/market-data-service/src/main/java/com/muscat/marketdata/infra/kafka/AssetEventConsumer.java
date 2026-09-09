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
        log.debug("종목 생성 이벤트 수신: symbol={}, collectData={}, trace={}",
                event.getSymbol(), event.isCollectData(), traceInfo);

        try {
            // 데이터 수집이 요청되지 않은 경우 바로 ACK
            if (!event.isCollectData()) {
                log.debug("데이터 수집 요청 없음: symbol={}", event.getSymbol());
                acknowledgment.acknowledge();
                return;
            }

            // 수집이 끝난 뒤에 ACK 한다. 먼저 커밋하면 파드가 내려갈 때
            // 아직 안 받은 종목의 오프셋까지 넘어가 그 이벤트가 다시 오지 않는다
            dataCollectionService.collect(event);

            acknowledgment.acknowledge();
            log.debug("수집 완료 후 ACK: symbol={}", event.getSymbol());

        } catch (Exception e) {
            // ACK 하지 않는다. 에러 핸들러가 재시도하고 남으면 .DLT 로 보낸다
            log.warn("종목 생성 이벤트 처리 실패: symbol={}, error={}",
                    event.getSymbol(), e.getMessage());
            throw e;
        }
    }

}
