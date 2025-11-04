package com.muscat.trade.infra.kafka;

import com.muscat.messaging.event.DividendReceivedEvent;
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
 * 배당금 수령 이벤트를 Kafka에 발행하는 Producer
 *
 * user-service의 동기 REST API 호출을 비동기 이벤트로 전환하여
 * 서비스 간 결합도를 낮추고 장애 격리를 구현합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendEventProducer {

    private static final String DIVIDEND_RECEIVED_TOPIC = "trading.dividend.received";

    private final KafkaTemplate<String, DividendReceivedEvent> dividendReceivedKafkaTemplate;

    /**
     * 배당금 수령 이벤트 발행
     *
     * @param userId 사용자 ID
     * @param accountId 계좌 ID
     * @param symbol 종목 심볼
     * @param exDate 배당 기준일
     * @param dividendPerShare 주당 배당금
     * @param quantity 보유 수량
     * @param totalAmount 총 배당금
     */
    public void publishDividendReceived(String userId, Long accountId, String symbol,
                                         String exDate, BigDecimal dividendPerShare,
                                         BigDecimal quantity, BigDecimal totalAmount) {
        String eventId = UUID.randomUUID().toString();

        // OpenTelemetry trace ID 추출
        String traceId = null;
        try {
            traceId = Span.current().getSpanContext().getTraceId();
        } catch (Exception e) {
            log.debug("TraceID 추출 실패: {}", e.getMessage());
        }

        DividendReceivedEvent event = DividendReceivedEvent.builder()
                .eventId(eventId)
                .eventType("DIVIDEND_RECEIVED")
                .timestamp(LocalDateTime.now())
                .version("1.0")
                .traceId(traceId)
                .source("trade-service")
                // Dividend 정보
                .userId(userId)
                .accountId(accountId)
                .symbol(symbol)
                .exDate(exDate)
                .dividendPerShare(dividendPerShare)
                .quantity(quantity)
                .totalAmount(totalAmount)
                .currency("USD")
                .build();

        log.info("배당금 수령 이벤트 발행 중: userId={}, accountId={}, symbol={}, amount={}",
                userId, accountId, symbol, totalAmount);

        // 비동기로 Kafka에 전송
        CompletableFuture<SendResult<String, DividendReceivedEvent>> future =
                dividendReceivedKafkaTemplate.send(DIVIDEND_RECEIVED_TOPIC, userId, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("배당금 수령 이벤트 발행 성공: topic={}, partition={}, offset={}, userId={}, symbol={}",
                        DIVIDEND_RECEIVED_TOPIC,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        userId, symbol);
            } else {
                log.error("배당금 수령 이벤트 발행 실패: userId={}, symbol={}, error={}",
                        userId, symbol, ex.getMessage(), ex);
            }
        });
    }
}
