package com.muscat.marketdata.infra.kafka;

import com.muscat.marketdata.domain.entity.Dividend;
import com.muscat.messaging.event.DividendUpdatedEvent;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 배당금 업데이트 이벤트를 Kafka에 발행하는 Producer
 *
 * 배당금 데이터가 저장될 때 DividendUpdatedEvent를 발행하여
 * trade-service 등이 포트폴리오에 배당금을 자동 반영할 수 있도록 합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendEventProducer {

    private static final String DIVIDEND_UPDATED_TOPIC = "market.dividend.updated";

    private final KafkaTemplate<String, DividendUpdatedEvent> kafkaTemplate;

    /**
     * 배당금 업데이트 이벤트 발행
     *
     * @param dividend 저장된 배당금 데이터
     */
    public void publishDividendUpdated(Dividend dividend) {
        String eventId = UUID.randomUUID().toString();

        // OpenTelemetry trace ID 추출
        String traceId = null;
        try {
            traceId = Span.current().getSpanContext().getTraceId();
        } catch (Exception e) {
            log.debug("TraceID 추출 실패: {}", e.getMessage());
        }

        DividendUpdatedEvent event = DividendUpdatedEvent.builder()
                .eventId(eventId)
                .eventType("DIVIDEND_UPDATED")
                .timestamp(LocalDateTime.now())
                .version("1.0")
                .traceId(traceId)
                .source("market-data-service")
                // Dividend 정보
                .symbol(dividend.getSymbol())
                .exDate(dividend.getExDate())
                .amount(dividend.getAmount())
                .currency(dividend.getCurrency())
                .build();

        log.debug("배당 업데이트 이벤트 발행 중: symbol={}, exDate={}, amount={}",
                dividend.getSymbol(), dividend.getExDate(), dividend.getAmount());

        // 비동기로 Kafka에 전송 (심볼을 파티션 키로 사용)
        CompletableFuture<SendResult<String, DividendUpdatedEvent>> future =
                kafkaTemplate.send(DIVIDEND_UPDATED_TOPIC, dividend.getSymbol(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.trace("배당 이벤트 발행 완료: topic={}, partition={}, offset={}, symbol={}",
                        DIVIDEND_UPDATED_TOPIC,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        dividend.getSymbol());
            } else {
                log.error("배당 이벤트 발행 실패: symbol={}, exDate={}, error={}",
                        dividend.getSymbol(), dividend.getExDate(), ex.getMessage(), ex);
            }
        });
    }

    /**
     * 여러 배당금 데이터에 대한 이벤트 일괄 발행
     *
     * @param dividends 저장된 배당금 데이터 리스트
     */
    public void publishBatch(List<Dividend> dividends) {
        if (dividends == null || dividends.isEmpty()) {
            return;
        }

        log.info("배당 이벤트 일괄 발행 중: {}건", dividends.size());
        dividends.forEach(this::publishDividendUpdated);
    }
}
