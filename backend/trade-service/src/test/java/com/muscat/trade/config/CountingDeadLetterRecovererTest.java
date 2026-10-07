package com.muscat.trade.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ConsumerAwareRecordRecoverer;

@DisplayName("DLT 로 넘긴 메시지 세기")
class CountingDeadLetterRecovererTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ConsumerAwareRecordRecoverer delegate = mock(ConsumerAwareRecordRecoverer.class);

    @Test
    @DisplayName("만들 때 0 으로 등록한다")
    void constructor_RegistersZero() {
        new CountingDeadLetterRecoverer(delegate, meterRegistry);

        assertThat(meterRegistry.get("kafka.dlt.records").counter().count()).isZero();
    }

    @Test
    @DisplayName("넘길 때 1 올리고 원래 복구기에 그대로 넘긴다")
    void accept_IncrementsAndDelegates() {
        CountingDeadLetterRecoverer recoverer = new CountingDeadLetterRecoverer(delegate, meterRegistry);
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("user.account.deleted", 0, 5L, "user-1", "{}");
        RuntimeException failure = new RuntimeException("db down");

        recoverer.accept(record, null, failure);

        assertThat(meterRegistry.get("kafka.dlt.records").counter().count()).isEqualTo(1.0);
        verify(delegate).accept(record, null, failure);
    }
}
