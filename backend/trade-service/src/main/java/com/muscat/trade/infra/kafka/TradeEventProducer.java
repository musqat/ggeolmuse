package com.muscat.trade.infra.kafka;

import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.messaging.kafka.EventPublishing;
import com.muscat.trade.common.enums.responses.TradeResponse;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.entity.Trade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Duration;

// Trade 체결 완료 이벤트 발행
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeEventProducer {

    private static final String TRADE_COMPLETED_TOPIC = "trading.trade.completed";
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);

    private final KafkaTemplate<String, TradeCompletedEvent> tradeCompletedKafkaTemplate;

    // 브로커 확인을 5초까지 기다리고, 받지 못하면 EVENT_PUBLISH_FAILED 로 올림
    public void publishTradeCompleted(Trade trade) {
        TradeCompletedEvent event = TradeCompletedEvent.builder()
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
                .build();
        EventPublishing.stamp(event, "TRADE_COMPLETED", "trade-service");

        log.info("거래 완료 이벤트 발행 중: tradeId={}, userId={}, symbol={}, amount={}",
                trade.getId(), trade.getUserId(), trade.getSymbol(), trade.getTotalAmount());

        SendResult<String, TradeCompletedEvent> result = EventPublishing.sendAndWait(tradeCompletedKafkaTemplate,
                TRADE_COMPLETED_TOPIC, trade.getUserId(), event, SEND_TIMEOUT,
                e -> new TradeException(TradeResponse.EVENT_PUBLISH_FAILED, e));
        log.info("거래 완료 이벤트 발행 성공: topic={}, partition={}, offset={}, tradeId={}",
                TRADE_COMPLETED_TOPIC,
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset(),
                trade.getId());
    }
}
