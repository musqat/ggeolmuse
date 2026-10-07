package com.muscat.trade.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.ConsumerAwareRecordRecoverer;
import org.springframework.lang.Nullable;

/**
 * 재시도로 못 살린 메시지를 DLT 로 넘기면서 kafka.dlt.records 를 올린다.
 */
@Slf4j
public class CountingDeadLetterRecoverer implements ConsumerAwareRecordRecoverer {

    private final ConsumerAwareRecordRecoverer delegate;
    private final Counter counter;

    public CountingDeadLetterRecoverer(ConsumerAwareRecordRecoverer delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        // 시작 때 0 으로 등록. 첫 증가 때 생긴 시계열은 increase 가 첫 값을 못 센다
        this.counter = Counter.builder("kafka.dlt.records").register(meterRegistry);
    }

    @Override
    public void accept(ConsumerRecord<?, ?> record, @Nullable Consumer<?, ?> consumer, Exception exception) {
        counter.increment();
        log.error("DLT 로 보냄: topic={}, partition={}, offset={}, error={}",
                record.topic(), record.partition(), record.offset(), exception.getMessage());
        delegate.accept(record, consumer, exception);
    }
}
