package com.muscat.marketdata.config;

import com.muscat.messaging.event.AssetCreatedEvent;
import com.muscat.messaging.kafka.KafkaFactories;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

// Market Data 서비스 Kafka 컨슈머 설정(종목 생성 이벤트를 받아 수집)
@EnableKafka
@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfig {

    private final MeterRegistry meterRegistry;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${marketdata.kafka.collection-concurrency:6}")
    private int concurrency;

    @Value("${marketdata.kafka.max-poll-records:1}")
    private int maxPollRecords;

    @Value("${marketdata.kafka.max-poll-interval-ms:600000}")
    private int maxPollIntervalMs;

    @Bean
    public ConsumerFactory<String, AssetCreatedEvent> assetEventConsumerFactory() {
        Map<String, Object> props = KafkaFactories.jsonConsumerProps(
                bootstrapServers, "market-data-collection-group", AssetCreatedEvent.class);

        // 리스너 안에서 수집을 끝내므로 한 번에 받는 건수가 곧 poll 주기가 된다.
        // 여러 건을 받으면 느린 종목 하나에 나머지가 묶여 max.poll.interval.ms 를 넘긴다. 한 건씩 받는다
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, maxPollIntervalMs);

        return KafkaFactories.consumerFactory(props, meterRegistry);
    }

    // market.asset.created 의 파티션 수와 같게 둔다. 넘으면 남는 스레드가 논다.
    // DB 커넥션 풀(20)보다 작아야 스레드가 커넥션을 다 쥐지 않는다
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AssetCreatedEvent>
    assetEventListenerFactory() {
        return KafkaFactories.manualAckContainer(assetEventConsumerFactory(), concurrency,
                KafkaFactories.deadLetterErrorHandler(dltKafkaTemplate(), meterRegistry));
    }

    /**
     * 재시도로 못 살린 메시지를 .DLT 토픽에 넣을 때 쓴다.
     */
    @Bean
    public KafkaTemplate<String, Object> dltKafkaTemplate() {
        return KafkaFactories.dltTemplate(bootstrapServers, meterRegistry);
    }
}
