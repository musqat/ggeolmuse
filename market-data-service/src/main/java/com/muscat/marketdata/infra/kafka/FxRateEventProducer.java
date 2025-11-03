package com.muscat.marketdata.infra.kafka;

import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.messaging.event.FxRateUpdatedEvent;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 환율 업데이트 이벤트를 Kafka에 발행하는 Producer
 *
 * 환율 데이터가 업데이트될 때 FxRateUpdatedEvent를 발행하여
 * user-service 등이 실시간으로 환율 정보를 반영할 수 있도록 합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FxRateEventProducer {

    private static final String FX_RATE_UPDATED_TOPIC = "market.fxrate.updated";
    private static final String DEFAULT_CURRENCY_PAIR = "USD/KRW";

    private final KafkaTemplate<String, FxRateUpdatedEvent> kafkaTemplate;

    /**
     * 환율 업데이트 이벤트 발행
     *
     * @param fxRate 업데이트된 환율 데이터
     */
    public void publishFxRateUpdated(FxRate fxRate) {
        publishFxRateUpdated(fxRate, null);
    }

    /**
     * 환율 업데이트 이벤트 발행 (이전 환율 포함)
     *
     * @param fxRate 업데이트된 환율 데이터
     * @param previousRate 이전 환율 (nullable)
     */
    public void publishFxRateUpdated(FxRate fxRate, BigDecimal previousRate) {
        String eventId = UUID.randomUUID().toString();

        // OpenTelemetry trace ID 추출
        String traceId = null;
        try {
            traceId = Span.current().getSpanContext().getTraceId();
        } catch (Exception e) {
            log.debug("TraceID 추출 실패: {}", e.getMessage());
        }

        FxRateUpdatedEvent event = FxRateUpdatedEvent.builder()
                .eventId(eventId)
                .eventType("FX_RATE_UPDATED")
                .timestamp(LocalDateTime.now())
                .version("1.0")
                .traceId(traceId)
                .source("market-data-service")
                // FxRate 정보
                .date(fxRate.getDate())
                .currencyPair(DEFAULT_CURRENCY_PAIR)
                .rate(fxRate.getRate())
                .previousRate(previousRate)
                .build();

        log.debug("환율 업데이트 이벤트 발행 중: date={}, rate={}, previousRate={}",
                fxRate.getDate(), fxRate.getRate(), previousRate);

        // 비동기로 Kafka에 전송 (날짜를 파티션 키로 사용)
        CompletableFuture<SendResult<String, FxRateUpdatedEvent>> future =
                kafkaTemplate.send(FX_RATE_UPDATED_TOPIC, fxRate.getDate().toString(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.trace("환율 이벤트 발행 완료: topic={}, partition={}, offset={}, date={}",
                        FX_RATE_UPDATED_TOPIC,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        fxRate.getDate());
            } else {
                log.error("환율 이벤트 발행 실패: date={}, rate={}, error={}",
                        fxRate.getDate(), fxRate.getRate(), ex.getMessage(), ex);
            }
        });
    }
}
