package com.muscat.marketdata.infra.kafka;

import com.muscat.messaging.event.DataCollectionCompletedEvent;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 데이터 수집 완료/실패 이벤트를 Kafka에 발행하는 Producer
 *
 * AssetCreatedEvent 처리 결과를 알리는데 사용됩니다.
 * 관리자 페이지에서 수집 현황을 추적할 수 있습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataCollectionEventProducer {

    private static final String COLLECTION_COMPLETED_TOPIC = "market.collection.completed";

    private final KafkaTemplate<String, DataCollectionCompletedEvent> collectionEventKafkaTemplate;

    /**
     * 데이터 수집 완료 이벤트 발행
     *
     * @param symbol 심볼
     * @param fromDate 수집 시작일
     * @param toDate 수집 종료일
     * @param candleCount 수집된 캔들 개수
     * @param dividendCount 수집된 배당 개수
     * @param executionTimeMs 실행 시간 (밀리초)
     */
    public void publishCompleted(String symbol, LocalDate fromDate, LocalDate toDate,
                                  Integer candleCount, Integer dividendCount,
                                  Long executionTimeMs) {
        String eventId = UUID.randomUUID().toString();

        // OpenTelemetry trace ID 추출
        String traceId = null;
        try {
            traceId = Span.current().getSpanContext().getTraceId();
        } catch (Exception e) {
            log.debug("TraceID 추출 실패: {}", e.getMessage());
        }

        DataCollectionCompletedEvent event = DataCollectionCompletedEvent.builder()
                .eventId(eventId)
                .eventType("COLLECTION_COMPLETED")
                .timestamp(LocalDateTime.now())
                .version("1.0")
                .traceId(traceId)
                .source("market-data-service")
                .symbol(symbol)
                .fromDate(fromDate)
                .toDate(toDate)
                .successful(true)
                .candleCount(candleCount)
                .dividendCount(dividendCount)
                .errorMessage(null)
                .executionTimeMs(executionTimeMs)
                .build();

        log.info("데이터 수집 완료 이벤트 발행 중: symbol={}, candleCount={}, dividendCount={}",
                symbol, candleCount, dividendCount);

        CompletableFuture<SendResult<String, DataCollectionCompletedEvent>> future =
                collectionEventKafkaTemplate.send(COLLECTION_COMPLETED_TOPIC, symbol, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("데이터 수집 완료 이벤트 발행 성공: topic={}, partition={}, offset={}, symbol={}",
                        COLLECTION_COMPLETED_TOPIC,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        symbol);
            } else {
                log.error("데이터 수집 완료 이벤트 발행 실패: symbol={}, error={}",
                        symbol, ex.getMessage(), ex);
            }
        });
    }

    /**
     * 데이터 수집 실패 이벤트 발행
     *
     */
    public void publishFailed(String symbol, LocalDate fromDate, LocalDate toDate,
                               String errorMessage, Long executionTimeMs) {
        String eventId = UUID.randomUUID().toString();

        // OpenTelemetry trace ID 추출
        String traceId = null;
        try {
            traceId = Span.current().getSpanContext().getTraceId();
        } catch (Exception e) {
            log.debug("TraceID 추출 실패: {}", e.getMessage());
        }

        DataCollectionCompletedEvent event = DataCollectionCompletedEvent.builder()
                .eventId(eventId)
                .eventType("COLLECTION_FAILED")
                .timestamp(LocalDateTime.now())
                .version("1.0")
                .traceId(traceId)
                .source("market-data-service")
                .symbol(symbol)
                .fromDate(fromDate)
                .toDate(toDate)
                .successful(false)
                .candleCount(0)
                .dividendCount(0)
                .errorMessage(errorMessage)
                .executionTimeMs(executionTimeMs)
                .build();

        log.warn("데이터 수집 실패 이벤트 발행 중: symbol={}, error={}",
                symbol, errorMessage);

        CompletableFuture<SendResult<String, DataCollectionCompletedEvent>> future =
                collectionEventKafkaTemplate.send(COLLECTION_COMPLETED_TOPIC, symbol, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("데이터 수집 실패 이벤트 발행 성공: topic={}, partition={}, offset={}, symbol={}",
                        COLLECTION_COMPLETED_TOPIC,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        symbol);
            } else {
                log.error("데이터 수집 실패 이벤트 발행 실패: symbol={}, error={}",
                        symbol, ex.getMessage(), ex);
            }
        });
    }
}
