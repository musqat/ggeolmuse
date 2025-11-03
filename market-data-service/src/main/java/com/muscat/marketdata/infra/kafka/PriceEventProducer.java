package com.muscat.marketdata.infra.kafka;

import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.messaging.event.PriceUpdatedEvent;
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
 * 주가 업데이트 이벤트를 Kafka에 발행하는 Producer
 *
 * 캔들 데이터가 업데이트될 때 PriceUpdatedEvent를 발행하여
 * trade-service, backtest-service 등이 실시간으로 주가 정보를 반영할 수 있도록 합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceEventProducer {

    private static final String PRICE_UPDATED_TOPIC = "market.price.updated";

    private final KafkaTemplate<String, PriceUpdatedEvent> kafkaTemplate;

    /**
     * 주가 업데이트 이벤트 발행
     *
     * @param candle 업데이트된 캔들 데이터
     */
    public void publishPriceUpdated(Candle candle) {
        String eventId = UUID.randomUUID().toString();

        // OpenTelemetry trace ID 추출
        String traceId = null;
        try {
            traceId = Span.current().getSpanContext().getTraceId();
        } catch (Exception e) {
            log.debug("TraceID 추출 실패: {}", e.getMessage());
        }

        PriceUpdatedEvent event = PriceUpdatedEvent.builder()
                .eventId(eventId)
                .eventType("PRICE_UPDATED")
                .timestamp(LocalDateTime.now())
                .version("1.0")
                .traceId(traceId)
                .source("market-data-service")
                // Price 정보
                .symbol(candle.getSymbol())
                .date(candle.getDate())
                .currency(candle.getCurrency())
                .open(candle.getOpen())
                .high(candle.getHigh())
                .low(candle.getLow())
                .close(candle.getClose())
                .adjustedClose(candle.getAdjustedClose())
                .volume(candle.getVolume())
                .dividendAmount(candle.getDividendAmount())
                .splitCoefficient(candle.getSplitCoefficient())
                .build();

        log.debug("주가 업데이트 이벤트 발행 중: symbol={}, date={}, adjustedClose={}",
                candle.getSymbol(), candle.getDate(), candle.getAdjustedClose());

        // 비동기로 Kafka에 전송 (심볼을 파티션 키로 사용)
        CompletableFuture<SendResult<String, PriceUpdatedEvent>> future =
                kafkaTemplate.send(PRICE_UPDATED_TOPIC, candle.getSymbol(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.trace("주가 이벤트 발행 완료: topic={}, partition={}, offset={}, symbol={}",
                        PRICE_UPDATED_TOPIC,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        candle.getSymbol());
            } else {
                log.error("주가 이벤트 발행 실패: symbol={}, date={}, error={}",
                        candle.getSymbol(), candle.getDate(), ex.getMessage(), ex);
            }
        });
    }

    /**
     * 여러 캔들 데이터에 대한 이벤트 일괄 발행
     *
     * @param candles 업데이트된 캔들 데이터 리스트
     */
    public void publishBatch(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return;
        }

        log.info("주가 이벤트 일괄 발행 중: {}건", candles.size());
        candles.forEach(this::publishPriceUpdated);
    }
}
