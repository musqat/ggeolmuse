package com.muscat.marketdata.infra.kafka;

import com.muscat.marketdata.domain.entity.Dividend;
import com.muscat.messaging.event.DividendUpdatedEvent;
import com.muscat.messaging.kafka.EventPublishing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

// 배당금 업데이트 이벤트 발행
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendEventProducer {

    private static final String DIVIDEND_UPDATED_TOPIC = "market.dividend.updated";

    private final KafkaTemplate<String, DividendUpdatedEvent> kafkaTemplate;

    public void publishDividendUpdated(Dividend dividend) {
        DividendUpdatedEvent event = DividendUpdatedEvent.builder()
                .symbol(dividend.getSymbol())
                .exDate(dividend.getExDate())
                .amount(dividend.getAmount())
                .currency(dividend.getCurrency())
                .build();
        EventPublishing.stamp(event, "DIVIDEND_UPDATED", "market-data-service");

        log.debug("배당 업데이트 이벤트 발행 중: symbol={}, exDate={}, amount={}",
                dividend.getSymbol(), dividend.getExDate(), dividend.getAmount());

        EventPublishing.send(kafkaTemplate, DIVIDEND_UPDATED_TOPIC, dividend.getSymbol(), event);
    }

    public void publishBatch(List<Dividend> dividends) {
        if (dividends == null || dividends.isEmpty()) {
            return;
        }

        log.debug("배당 이벤트 일괄 발행 중: {}건", dividends.size());
        dividends.forEach(this::publishDividendUpdated);
    }
}
