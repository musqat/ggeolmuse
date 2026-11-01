package com.muscat.trade.config;

import com.muscat.messaging.event.AccountDeletedEvent;
import com.muscat.messaging.event.DividendUpdatedEvent;
import com.muscat.messaging.event.TradeRejectedEvent;
import com.muscat.messaging.event.TradeSettledEvent;
import com.muscat.messaging.kafka.KafkaFactories;
import com.muscat.trade.domain.event.DividendReconcileRequest;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;

// Trade 서비스 Kafka 컨슈머 설정
@EnableKafka
@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfig {

    private final MeterRegistry meterRegistry;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.application.name}")
    private String applicationName;

    @Bean
    public ConsumerFactory<String, DividendUpdatedEvent> dividendEventConsumerFactory() {
        return consumerFactory(DividendUpdatedEvent.class, "-dividend-consumer");
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DividendUpdatedEvent>
    dividendEventKafkaListenerContainerFactory() {
        return KafkaFactories.manualAckContainer(dividendEventConsumerFactory(), 1, kafkaErrorHandler());
    }

    @Bean
    public ConsumerFactory<String, DividendReconcileRequest> dividendReconcileConsumerFactory() {
        return consumerFactory(DividendReconcileRequest.class, "-dividend-reconcile-consumer");
    }

    // 한 스레드가 차례로 처리해 같은 계좌의 재계산이 겹치지 않음
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DividendReconcileRequest>
    dividendReconcileKafkaListenerContainerFactory() {
        return KafkaFactories.manualAckContainer(dividendReconcileConsumerFactory(), 1, kafkaErrorHandler());
    }

    @Bean
    public ConsumerFactory<String, AccountDeletedEvent> accountDeletedEventConsumerFactory() {
        return consumerFactory(AccountDeletedEvent.class, "-account-deleted-consumer");
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AccountDeletedEvent>
    accountDeletedEventKafkaListenerContainerFactory() {
        return KafkaFactories.manualAckContainer(accountDeletedEventConsumerFactory(), 1, kafkaErrorHandler());
    }

    @Bean
    public ConsumerFactory<String, TradeRejectedEvent> tradeRejectedEventConsumerFactory() {
        return consumerFactory(TradeRejectedEvent.class, "-trade-rejected-consumer");
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TradeRejectedEvent>
    tradeRejectedEventKafkaListenerContainerFactory() {
        return KafkaFactories.manualAckContainer(tradeRejectedEventConsumerFactory(), 1, kafkaErrorHandler());
    }

    @Bean
    public ConsumerFactory<String, TradeSettledEvent> tradeSettledEventConsumerFactory() {
        return consumerFactory(TradeSettledEvent.class, "-trade-settled-consumer");
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TradeSettledEvent>
    tradeSettledEventKafkaListenerContainerFactory() {
        return KafkaFactories.manualAckContainer(tradeSettledEventConsumerFactory(), 1, kafkaErrorHandler());
    }

    /**
     * 재시도로 못 살린 메시지를 .DLT 토픽에 넣는 템플릿
     */
    @Bean
    public KafkaTemplate<String, Object> dltKafkaTemplate() {
        return KafkaFactories.dltTemplate(bootstrapServers, meterRegistry);
    }

    private <T> ConsumerFactory<String, T> consumerFactory(Class<T> type, String groupSuffix) {
        return KafkaFactories.jsonConsumerFactory(bootstrapServers, applicationName + groupSuffix, type, meterRegistry);
    }

    private CommonErrorHandler kafkaErrorHandler() {
        return KafkaFactories.deadLetterErrorHandler(dltKafkaTemplate(), meterRegistry);
    }
}
