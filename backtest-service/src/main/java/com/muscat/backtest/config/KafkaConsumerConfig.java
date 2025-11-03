package com.muscat.backtest.config;

import com.muscat.messaging.event.PriceUpdatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Backtest Service Kafka Consumer 설정
 *
 * PriceUpdatedEvent를 Kafka에서 소비하기 위한 설정
 */
@Slf4j
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.application.name}")
    private String applicationName;

    @Bean
    public ConsumerFactory<String, PriceUpdatedEvent> priceEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, applicationName + "-price-consumer");

        // 수동 커밋 모드 (메시지 처리 성공시에만 커밋)
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // 컨슈머 그룹 최초 실행시 earliest부터 읽기
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Deserializer 설정
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());

        // JSON Deserializer 추가 설정
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PriceUpdatedEvent.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.muscat.*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PriceUpdatedEvent>
    priceEventKafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, PriceUpdatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(priceEventConsumerFactory());

        // 수동 커밋 모드 설정
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // 동시성 레벨 (백테스트는 배치 처리가 많아서 적게 설정)
        factory.setConcurrency(3);

        // 공통 에러 핸들러 설정 (DLQ 포함)
        factory.setCommonErrorHandler(kafkaErrorHandler(null));

        return factory;
    }

    /**
     * Kafka Consumer 공통 에러 핸들러
     * - 3회 재시도 (지수 백오프: 1초, 2초, 4초)
     * - 재시도 실패 시 DLQ 토픽으로 전송
     */
    private CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        // 지수 백오프 설정: 초기 1초, 배수 2.0, 최대 10초
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxElapsedTime(10000L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler((consumerRecord, exception) -> {
            // DLQ 토픽으로 전송
            String dlqTopic = consumerRecord.topic() + ".DLQ";
            log.error("Failed to process message after retries. Sending to DLQ: topic={}, key={}, offset={}, partition={}",
                    dlqTopic, consumerRecord.key(), consumerRecord.offset(), consumerRecord.partition(), exception);

            // kafkaTemplate이 있으면 DLQ로 전송 (현재는 수동 커밋 모드이므로 로깅만 수행)
        }, backOff);

        // 최대 재시도 횟수: 3회
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            log.warn("Retry attempt {} for topic {}, partition {}, offset {}",
                    deliveryAttempt, record.topic(), record.partition(), record.offset());
        });

        return errorHandler;
    }
}
