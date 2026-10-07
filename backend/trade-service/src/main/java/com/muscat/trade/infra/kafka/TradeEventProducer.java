package com.muscat.trade.infra.kafka;

import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.trade.domain.entity.Trade;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Trade 이벤트를 Kafka에 발행하는 Producer
 *
 * 체결 완료 이벤트를 발행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeEventProducer {

    private static final String TRADE_COMPLETED_TOPIC = "trading.trade.completed";
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, TradeCompletedEvent> tradeCompletedKafkaTemplate;

    /**
     * 거래 완료 이벤트 발행. 브로커 확인을 5초까지 기다리고, 받지 못하면 IllegalStateException 을 낸다.
     *
     * @param trade 체결된 거래 정보
     */
    public void publishTradeCompleted(Trade trade) {
        String eventId = UUID.randomUUID().toString();

        // OpenTelemetry trace ID 추출
        String traceId = null;
        try {
            traceId = Span.current().getSpanContext().getTraceId();
        } catch (Exception e) {
            log.debug("TraceID 추출 실패: {}", e.getMessage());
        }

        TradeCompletedEvent event = TradeCompletedEvent.builder()
                .eventId(eventId)
                .eventType("TRADE_COMPLETED")
                .timestamp(LocalDateTime.now())
                .version("1.0")
                .traceId(traceId)
                .source("trade-service")
                // Trade 정보
                .userId(trade.getUserId())
                .tradeId(trade.getId())
                .accountId(trade.getAccountId())
                .symbol(trade.getSymbol())
                .tradeType(trade.getTradeType().name())
                .quantity(trade.getQuantity())
                .price(trade.getPrice())
                .totalAmount(trade.getTotalAmount())
                .currency("USD") // 모든 거래는 USD 기준
                .fee(trade.getFee())
                .priceType("MARKET") // 현재는 시장가 거래만 지원
                .build();

        log.info("거래 완료 이벤트 발행 중: tradeId={}, userId={}, symbol={}, amount={}",
                trade.getId(), trade.getUserId(), trade.getSymbol(), trade.getTotalAmount());

        try {
            SendResult<String, TradeCompletedEvent> result = tradeCompletedKafkaTemplate
                    .send(TRADE_COMPLETED_TOPIC, trade.getUserId(), event)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("거래 완료 이벤트 발행 성공: topic={}, partition={}, offset={}, tradeId={}",
                    TRADE_COMPLETED_TOPIC,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    trade.getId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("거래 완료 이벤트 발행 중단: tradeId=" + trade.getId(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("거래 완료 이벤트 발행 실패: tradeId=" + trade.getId(), e);
        }
    }
}
