package com.muscat.trade.config;

import com.muscat.messaging.event.DividendPayoutEvent;
import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.messaging.kafka.KafkaFactories;
import com.muscat.trade.domain.event.DividendReconcileRequest;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

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
        Map<String, Object> configProps = KafkaFactories.producerProps(bootstrapServers);
        configProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000); // 브로커를 못 찾으면 5초 안에 실패 (기본 60초)
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
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
