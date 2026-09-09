package com.muscat.marketdata.config;

import com.muscat.messaging.event.AssetCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Market Data Service Kafka Consumer 설정
 * AssetCreatedEvent를 소비하여 데이터 수집
 */
@Slf4j
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${marketdata.kafka.collection-concurrency:6}")
    private int concurrency;

    @Value("${marketdata.kafka.max-poll-records:5}")
    private int maxPollRecords;

    @Value("${marketdata.kafka.max-poll-interval-ms:600000}")
    private int maxPollIntervalMs;

    @Bean
    public ConsumerFactory<String, AssetCreatedEvent> assetEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "market-data-collection-group");

        // 수동 커밋 모드 (메시지 처리 성공시에만 커밋)
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // 컨슈머 그룹 최초 실행시 earliest부터 읽기
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // 리스너 안에서 수집을 끝내므로 한 번에 받는 건수가 곧 poll 주기가 된다.
        // 기본값 500 이면 종목당 몇 초만 걸려도 max.poll.interval.ms 를 넘겨 리밸런스가 돈다
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, maxPollIntervalMs);

        // Deserializer 설정
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());

        // JSON Deserializer 추가 설정
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, AssetCreatedEvent.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.muscat.*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AssetCreatedEvent>
    assetEventListenerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, AssetCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(assetEventConsumerFactory());

        // 수동 커밋 모드 설정
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // market.asset.created 의 파티션 수와 같게 둔다. 넘으면 남는 스레드가 논다.
        // DB 커넥션 풀(20)보다 작아야 스레드가 커넥션을 다 쥐지 않는다
        factory.setConcurrency(concurrency);

        factory.setCommonErrorHandler(kafkaErrorHandler());

        return factory;
    }

    /**
     * 재시도로 못 살린 메시지를 .DLT 토픽에 넣을 때 쓴다.
     */
    @Bean
    public KafkaTemplate<String, Object> dltKafkaTemplate() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    /**
     * 1초 간격으로 3회 재시도한다. 그래도 안 되면 토픽 이름에 .DLT 를 붙인 곳으로 보낸다.
     */
    private CommonErrorHandler kafkaErrorHandler() {
        // 파티션을 -1 로 두면 카프카가 고른다. DLT 파티션 수가 원본보다 적어도 된다
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dltKafkaTemplate(),
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1));

        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));

        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            log.warn("재시도 {}/3: topic={}, partition={}, offset={}, error={}",
                    deliveryAttempt, record.topic(), record.partition(), record.offset(), ex.getMessage());
        });

        return errorHandler;
    }
}
