package com.muscat.trade.config;

import com.muscat.messaging.event.DividendPayoutEvent;
import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.trade.domain.event.DividendReconcileRequest;
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

// Trade 서비스 Kafka 프로듀서 설정
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * 공통 Producer 설정 생성
     */
    private Map<String, Object> getCommonProducerConfig() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Producer 안정성 설정
        configProps.put(ProducerConfig.ACKS_CONFIG, "all"); // 모든 replica 확인
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3); // 3번 재시도
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // 멱등성 보장
        configProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000); // 브로커를 못 찾으면 5초 안에 실패 (기본 60초)

        // 성능 최적화
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip"); // Docker 호환성
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10); // 10ms 배치 대기
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384); // 16KB 배치

        return configProps;
    }

    @Bean
    public ProducerFactory<String, TradeCompletedEvent> tradeEventProducerFactory() {
        return new DefaultKafkaProducerFactory<>(getCommonProducerConfig());
    }

    @Bean
    public KafkaTemplate<String, TradeCompletedEvent> tradeCompletedKafkaTemplate() {
        return new KafkaTemplate<>(tradeEventProducerFactory());
    }

    @Bean
    public ProducerFactory<String, DividendReconcileRequest> dividendReconcileProducerFactory() {
        return new DefaultKafkaProducerFactory<>(getCommonProducerConfig());
    }

    @Bean
    public KafkaTemplate<String, DividendReconcileRequest> dividendReconcileKafkaTemplate() {
        return new KafkaTemplate<>(dividendReconcileProducerFactory());
    }

    @Bean
    public ProducerFactory<String, DividendPayoutEvent> dividendPayoutEventProducerFactory() {
        return new DefaultKafkaProducerFactory<>(getCommonProducerConfig());
    }

    @Bean
    public KafkaTemplate<String, DividendPayoutEvent> dividendPayoutKafkaTemplate() {
        return new KafkaTemplate<>(dividendPayoutEventProducerFactory());
    }
}
