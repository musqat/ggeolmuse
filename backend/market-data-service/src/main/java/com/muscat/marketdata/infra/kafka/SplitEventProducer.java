package com.muscat.marketdata.infra.kafka;

import com.muscat.messaging.event.StockSplitEvent;
import com.muscat.messaging.kafka.EventPublishing;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// 주식 분할 이벤트 발행(종목 코드가 키)
@Slf4j
@Component
@RequiredArgsConstructor
public class SplitEventProducer {

    private static final String SPLIT_DETECTED_TOPIC = "market.split.detected";

    private final KafkaTemplate<String, StockSplitEvent> splitEventKafkaTemplate;

    public void publishSplitDetected(String symbol, LocalDate splitDate, BigDecimal ratio) {
        StockSplitEvent event = StockSplitEvent.builder()
                .symbol(symbol)
                .splitDate(splitDate)
                .ratio(ratio)
                .build();
        EventPublishing.stamp(event, "SPLIT_DETECTED", "market-data-service");

        log.info("분할 이벤트 발행: symbol={}, splitDate={}, ratio={}", symbol, splitDate, ratio);

        EventPublishing.sendAfterCommit(splitEventKafkaTemplate, SPLIT_DETECTED_TOPIC, symbol, event);
    }
}
