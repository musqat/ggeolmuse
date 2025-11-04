package com.muscat.trade.infra.kafka;

import com.muscat.messaging.event.HoldingsUpdatedEvent;
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
 * Holdings변경 이벤트를 Kafka에 발행하는 Producer
 *
 * 거래 체결 후 포트폴리오 변경 사항을 이벤트로 발행하여
 * 다른 서비스들이 실시간으로 포트폴리오 변경을 추적할 수 있도록 합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoldingsEventProducer {

    private static final String HOLDINGS_UPDATED_TOPIC = "trading.holdings.updated";

    private final KafkaTemplate<String, HoldingsUpdatedEvent> holdingsUpdatedKafkaTemplate;

    /**
     * Holdings 업데이트 이벤트 발행
     *
     * @param userId 사용자 ID
     * @param accountId 계좌 ID
     * @param symbol 종목 심볼
     * @param updateType 업데이트 타입 ("CREATED", "UPDATED", "DELETED")
     * @param previousQuantity 변경 전 수량
     * @param currentQuantity 변경 후 수량
     * @param previousAvgPrice 변경 전 평균 단가
     * @param currentAvgPrice 변경 후 평균 단가
     * @param totalInvestedAmount 총 투자 금액
     * @param tradeId 거래 ID
     * @param tradeType 거래 타입 ("BUY", "SELL")
     * @param tradeQuantity 거래 수량
     * @param tradePrice 거래 단가
     */
    public void publishHoldingsUpdated(String userId, Long accountId, String symbol,
                                        String updateType,
                                        BigDecimal previousQuantity, BigDecimal currentQuantity,
                                        BigDecimal previousAvgPrice, BigDecimal currentAvgPrice,
                                        BigDecimal totalInvestedAmount,
                                        String tradeId, String tradeType,
                                        BigDecimal tradeQuantity, BigDecimal tradePrice) {
        String eventId = UUID.randomUUID().toString();

        // OpenTelemetry trace ID 추출
        String traceId = null;
        try {
            traceId = Span.current().getSpanContext().getTraceId();
        } catch (Exception e) {
            log.debug("TraceID 추출 실패: {}", e.getMessage());
        }

        HoldingsUpdatedEvent event = HoldingsUpdatedEvent.builder()
                .eventId(eventId)
                .eventType("HOLDINGS_UPDATED")
                .timestamp(LocalDateTime.now())
                .version("1.0")
                .traceId(traceId)
                .source("trade-service")
                // Holdings 정보
                .userId(userId)
                .accountId(accountId)
                .symbol(symbol)
                .updateType(updateType)
                .previousQuantity(previousQuantity)
                .currentQuantity(currentQuantity)
                .previousAvgPrice(previousAvgPrice)
                .currentAvgPrice(currentAvgPrice)
                .totalInvestedAmount(totalInvestedAmount)
                // Trade 정보
                .tradeId(tradeId)
                .tradeType(tradeType)
                .tradeQuantity(tradeQuantity)
                .tradePrice(tradePrice)
                .build();

        log.info("Holdings 업데이트 이벤트 발행 중: userId={}, symbol={}, updateType={}, {} -> {}",
                userId, symbol, updateType, previousQuantity, currentQuantity);

        // 비동기로 Kafka에 전송
        CompletableFuture<SendResult<String, HoldingsUpdatedEvent>> future =
                holdingsUpdatedKafkaTemplate.send(HOLDINGS_UPDATED_TOPIC, userId, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Holdings 업데이트 이벤트 발행 성공: topic={}, partition={}, offset={}, symbol={}, updateType={}",
                        HOLDINGS_UPDATED_TOPIC,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        symbol, updateType);
            } else {
                log.error("Holdings 업데이트 이벤트 발행 실패: symbol={}, updateType={}, error={}",
                        symbol, updateType, ex.getMessage(), ex);
            }
        });
    }
}
