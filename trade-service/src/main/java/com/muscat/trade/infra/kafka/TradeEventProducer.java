package com.muscat.trade.infra.kafka;

import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.messaging.event.TradeFailedEvent;
import com.muscat.trade.domain.entity.Trade;
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
 * Trade 이벤트를 Kafka에 발행하는 Producer
 *
 * 거래 체결 완료/실패시 이벤트를 발행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeEventProducer {

    private static final String TRADE_COMPLETED_TOPIC = "trading.trade.completed";
    private static final String TRADE_FAILED_TOPIC = "trading.trade.failed";

    private final KafkaTemplate<String, TradeCompletedEvent> tradeCompletedKafkaTemplate;
    private final KafkaTemplate<String, TradeFailedEvent> tradeFailedKafkaTemplate;

    /**
     * 거래 완료 이벤트 발행
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
                .tradeId(trade.getTradeId())
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
                trade.getTradeId(), trade.getUserId(), trade.getSymbol(), trade.getTotalAmount());

        // 비동기로 Kafka에 전송
        CompletableFuture<SendResult<String, TradeCompletedEvent>> future =
                tradeCompletedKafkaTemplate.send(TRADE_COMPLETED_TOPIC, trade.getUserId(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("거래 완료 이벤트 발행 성공: topic={}, partition={}, offset={}, tradeId={}",
                        TRADE_COMPLETED_TOPIC,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        trade.getTradeId());
            } else {
                log.error("거래 완료 이벤트 발행 실패: tradeId={}, error={}",
                        trade.getTradeId(), ex.getMessage(), ex);
            }
        });
    }

    /**
     * 거래 실패 이벤트 발행
     *
     * @param userId 사용자 ID
     * @param accountId 계좌 ID
     * @param symbol 종목 심볼
     * @param tradeType 거래 타입
     * @param quantity 주문 수량
     * @param price 주문 가격
     * @param failureCode 실패 원인 코드
     * @param failureMessage 실패 원인 메시지
     */
    public void publishTradeFailed(String userId, Long accountId, String symbol, String tradeType,
                                    Integer quantity, BigDecimal price,
                                    String failureCode, String failureMessage) {
        String eventId = UUID.randomUUID().toString();

        // OpenTelemetry trace ID 추출
        String traceId = null;
        try {
            traceId = Span.current().getSpanContext().getTraceId();
        } catch (Exception e) {
            log.debug("TraceID 추출 실패: {}", e.getMessage());
        }

        BigDecimal totalAmount = price.multiply(BigDecimal.valueOf(quantity));

        TradeFailedEvent event = TradeFailedEvent.builder()
                .eventId(eventId)
                .eventType("TRADE_FAILED")
                .timestamp(LocalDateTime.now())
                .version("1.0")
                .traceId(traceId)
                .source("trade-service")
                // Trade 정보
                .userId(userId)
                .accountId(accountId)
                .symbol(symbol)
                .tradeType(tradeType)
                .quantity(quantity)
                .price(price)
                .totalAmount(totalAmount)
                .currency("USD")
                .priceType("MARKET")
                .failureCode(failureCode)
                .failureMessage(failureMessage)
                .build();

        log.info("거래 실패 이벤트 발행 중: userId={}, symbol={}, tradeType={}, failureCode={}",
                userId, symbol, tradeType, failureCode);

        // 비동기로 Kafka에 전송
        CompletableFuture<SendResult<String, TradeFailedEvent>> future =
                tradeFailedKafkaTemplate.send(TRADE_FAILED_TOPIC, userId, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("거래 실패 이벤트 발행 성공: topic={}, partition={}, offset={}, symbol={}",
                        TRADE_FAILED_TOPIC,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        symbol);
            } else {
                log.error("거래 실패 이벤트 발행 실패: symbol={}, error={}",
                        symbol, ex.getMessage(), ex);
            }
        });
    }
}
