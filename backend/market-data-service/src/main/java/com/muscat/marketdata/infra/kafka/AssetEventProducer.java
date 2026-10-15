package com.muscat.marketdata.infra.kafka;

import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.messaging.event.AssetCreatedEvent;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Asset 생성 이벤트를 Kafka에 발행하는 Producer
 *
 * 관리자가 새로운 심볼을 등록할 때 이벤트를 발행하여,
 * 데이터 수집이 필요한 경우 컨슈머가 비동기로 처리합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetEventProducer {

    private static final String ASSET_CREATED_TOPIC = "market.asset.created";

    private final KafkaTemplate<String, AssetCreatedEvent> assetCreatedKafkaTemplate;

    /**
     * 심볼 생성 이벤트 발행
     */
    public void publishAssetCreated(Asset asset, boolean collectData,
                                     LocalDate fromDate, LocalDate toDate,
                                     boolean includeDividends) {
        String eventId = UUID.randomUUID().toString();

        // OpenTelemetry trace ID 추출
        String traceId = null;
        try {
            String spanTraceId = Span.current().getSpanContext().getTraceId();
            // Invalid span (all zeros) 체크
            if (spanTraceId != null && !spanTraceId.matches("0+")) {
                traceId = spanTraceId;
            } else {
                // 배치 수집 등 trace context가 없는 경우
                traceId = "BATCH_COLLECTION";
            }
        } catch (Exception e) {
            traceId = "BATCH_COLLECTION";
            log.debug("TraceID 추출 실패, BATCH_COLLECTION 사용: {}", e.getMessage());
        }

        AssetCreatedEvent event = AssetCreatedEvent.builder()
                .eventId(eventId)
                .eventType("ASSET_CREATED")
                .timestamp(LocalDateTime.now())
                .version("1.0")
                .traceId(traceId)
                .source("market-data-service")
                // Asset 정보
                .symbol(asset.getSymbol())
                .name(asset.getName())
                .country(asset.getCountry())
                .currency(asset.getCurrency())
                .assetType(asset.getAssetType())
                .marketCap(asset.getMarketCap())
                // 수집 옵션
                .collectData(collectData)
                .fromDate(fromDate)
                .toDate(toDate)
                .includeDividends(includeDividends)
                .build();

        log.debug("종목 생성 이벤트 발행 중: symbol={}, collectData={}",
                asset.getSymbol(), collectData);

        // 트랜잭션 안이면 커밋 뒤에 전송. 먼저 보내면 수집 컨슈머가 빈 종목을 봄
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendAssetCreated(event);
                }
            });
        } else {
            sendAssetCreated(event);
        }
    }

    // 전송 실패는 로그만. 종목 등록은 이미 커밋됨
    private void sendAssetCreated(AssetCreatedEvent event) {
        try {
            CompletableFuture<SendResult<String, AssetCreatedEvent>> future =
                    assetCreatedKafkaTemplate.send(ASSET_CREATED_TOPIC, event.getSymbol(), event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug("종목 생성 이벤트 발행 성공: topic={}, partition={}, offset={}, symbol={}",
                            ASSET_CREATED_TOPIC,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset(),
                            event.getSymbol());
                } else {
                    log.error("종목 생성 이벤트 발행 실패: symbol={}, error={}",
                            event.getSymbol(), ex.getMessage(), ex);
                }
            });
        } catch (Exception e) {
            log.error("종목 생성 이벤트 발행 실패: symbol={}, error={}", event.getSymbol(), e.getMessage(), e);
        }
    }
}
