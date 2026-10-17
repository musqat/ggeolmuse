package com.muscat.trade.infra.kafka;

import com.muscat.messaging.event.DividendReceivedEvent;
import com.muscat.trade.common.enums.responses.TradeResponse;
import com.muscat.trade.common.exception.TradeException;
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

// 배당금 수령 이벤트 발행
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendEventProducer {

    private static final String DIVIDEND_RECEIVED_TOPIC = "trading.dividend.received";
    // 체결 이벤트와 같은 값. 브로커가 이 안에 받지 못하면 실패로 본다
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, DividendReceivedEvent> dividendReceivedKafkaTemplate;

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
            throw new TradeException(TradeResponse.EVENT_PUBLISH_FAILED, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new TradeException(TradeResponse.EVENT_PUBLISH_FAILED, e);
        }
    }
}
