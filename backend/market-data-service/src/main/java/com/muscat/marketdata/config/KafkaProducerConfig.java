package com.muscat.marketdata.config;

import com.muscat.messaging.event.AssetCreatedEvent;
import com.muscat.messaging.event.DividendUpdatedEvent;
import com.muscat.messaging.kafka.KafkaFactories;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Map;

// Market Data 서비스 Kafka 프로듀서 설정
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, DividendUpdatedEvent> dividendEventProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerProps());
    }

    @Bean
    public KafkaTemplate<String, DividendUpdatedEvent> dividendEventKafkaTemplate() {
        return new KafkaTemplate<>(dividendEventProducerFactory());
    }

    @Bean
    public ProducerFactory<String, AssetCreatedEvent> assetCreatedProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerProps());
    }

    @Bean
    public KafkaTemplate<String, AssetCreatedEvent> assetCreatedKafkaTemplate() {
        return new KafkaTemplate<>(assetCreatedProducerFactory());
    }

    private Map<String, Object> producerProps() {
        Map<String, Object> props = KafkaFactories.producerProps(bootstrapServers);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");
        props.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        return props;
    }
}
