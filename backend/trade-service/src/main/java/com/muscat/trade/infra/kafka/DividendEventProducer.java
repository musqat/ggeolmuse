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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    // 체결 이벤트와 같은 값. 브로커가 이 안에 받지 못하면 실패로 본다
    private static final long SEND_TIMEOUT_SECONDS = 5;

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

        // 브로커가 받은 것까지 확인한다. 실패를 지급 루프가 알아야 커밋하지 않고 다시 받는다
        try {
            SendResult<String, DividendReceivedEvent> result = dividendReceivedKafkaTemplate
                    .send(DIVIDEND_RECEIVED_TOPIC, userId, event)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("배당금 수령 이벤트 발행 성공: topic={}, partition={}, offset={}, userId={}, symbol={}",
                    DIVIDEND_RECEIVED_TOPIC,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    userId, symbol);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("배당금 수령 이벤트 발행 중단: accountId=" + accountId + ", symbol=" + symbol, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("배당금 수령 이벤트 발행 실패: accountId=" + accountId + ", symbol=" + symbol, e);
        }
    }
}
