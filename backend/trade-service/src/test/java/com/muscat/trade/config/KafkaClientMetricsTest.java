package com.muscat.trade.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.AbstractKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.MicrometerConsumerListener;
import org.springframework.kafka.core.MicrometerProducerListener;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("Kafka 클라이언트 지표")
class KafkaClientMetricsTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    @DisplayName("프로듀서 팩토리마다 전송 지표 리스너가 있다")
    void everyProducerFactoryHasMetricsListener() throws Exception {
        List<ProducerFactory<?, ?>> factories = new ArrayList<>();
        for (Object bean : beans(producerConfig(), consumerConfig())) {
            if (bean instanceof ProducerFactory<?, ?> factory) {
                factories.add(factory);
            } else if (bean instanceof KafkaTemplate<?, ?> template) {
                factories.add(template.getProducerFactory());
            }
        }

        assertThat(factories).isNotEmpty().allSatisfy(factory ->
                assertThat(factory.getListeners()).hasAtLeastOneElementOfType(MicrometerProducerListener.class));
    }

    @Test
    @DisplayName("컨슈머 팩토리마다 지연 지표 리스너가 있다")
    void everyConsumerFactoryHasMetricsListener() throws Exception {
        List<ConsumerFactory<?, ?>> factories = new ArrayList<>();
        for (Object bean : beans(consumerConfig())) {
            if (bean instanceof ConsumerFactory<?, ?> factory) {
                factories.add(factory);
            } else if (bean instanceof AbstractKafkaListenerContainerFactory<?, ?, ?> container) {
                factories.add(container.getConsumerFactory());
            }
        }

        assertThat(factories).isNotEmpty().allSatisfy(factory ->
                assertThat(factory.getListeners()).hasAtLeastOneElementOfType(MicrometerConsumerListener.class));
    }

    private KafkaProducerConfig producerConfig() {
        KafkaProducerConfig config = new KafkaProducerConfig(meterRegistry);
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        return config;
    }

    private KafkaConsumerConfig consumerConfig() {
        KafkaConsumerConfig config = new KafkaConsumerConfig(meterRegistry);
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(config, "applicationName", "trade-service");
        return config;
    }

    // 설정 클래스의 인자 없는 @Bean 메서드가 만드는 객체 전부
    private static List<Object> beans(Object... configs) throws Exception {
        List<Object> beans = new ArrayList<>();
        for (Object config : configs) {
            for (Method method : config.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(Bean.class) && method.getParameterCount() == 0) {
                    beans.add(method.invoke(config));
                }
            }
        }
        return beans;
    }
}
