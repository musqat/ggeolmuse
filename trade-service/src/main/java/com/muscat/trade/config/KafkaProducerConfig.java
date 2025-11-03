package com.muscat.trade.config;

import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.messaging.event.TradeFailedEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Trade Service Kafka Producer 설정
 *
 * TradeCompletedEvent, TradeFailedEvent를 Kafka에 발행하기 위한 설정
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, TradeCompletedEvent> tradeEventProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Producer 안정성 설정
        configProps.put(ProducerConfig.ACKS_CONFIG, "all"); // 모든 replica 확인
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3); // 3번 재시도
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // 멱등성 보장

        // 성능 최적화
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip"); // Docker 호환성
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10); // 10ms 배치 대기
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384); // 16KB 배치

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, TradeCompletedEvent> tradeEventKafkaTemplate() {
        return new KafkaTemplate<>(tradeEventProducerFactory());
    }

    @Bean
    public ProducerFactory<String, TradeFailedEvent> tradeFailedEventProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Producer 안정성 설정
        configProps.put(ProducerConfig.ACKS_CONFIG, "all"); // 모든 replica 확인
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3); // 3번 재시도
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // 멱등성 보장

        // 성능 최적화
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip"); // Docker 호환성
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10); // 10ms 배치 대기
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384); // 16KB 배치

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, TradeFailedEvent> tradeFailedEventKafkaTemplate() {
        return new KafkaTemplate<>(tradeFailedEventProducerFactory());
    }
}
