package com.muscat.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

import com.muscat.messaging.event.TradeCompletedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.mock.MockProducerFactory;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

@DisplayName("서비스 Kafka 설정 공통 값")
class KafkaFactoriesTest {

    private static final String BOOTSTRAP = "kafka:9092";
    // 클라이언트를 실제로 만드는 테스트용(브로커가 없어도 만들어지고 이름이 풀리는지)
    private static final String LOCAL_BOOTSTRAP = "localhost:9092";

    @Test
    @DisplayName("프로듀서는 문자열 키 · JSON 값에 acks=all · 재시도 3 · 멱등 전송이고, 서비스가 값을 더할 수 있다")
    void producerProps() {
        Map<String, Object> props = KafkaFactories.producerProps(BOOTSTRAP);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");

        assertThat(props)
                .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP)
                .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
                .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class)
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                .containsEntry(ProducerConfig.RETRIES_CONFIG, 3)
                .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
                .containsEntry(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");
    }

    @Test
    @DisplayName("컨슈머는 수동 커밋 · earliest 이고 값은 타입 헤더 없이 지정한 타입으로 읽는다")
    void jsonConsumerProps() {
        Map<String, Object> props =
                KafkaFactories.jsonConsumerProps(BOOTSTRAP, "user-service-trade-consumer", TradeCompletedEvent.class);

        assertThat(props)
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP)
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "user-service-trade-consumer")
                .containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
                .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                .containsEntry(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class)
                .containsEntry(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class)
                .containsEntry(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName())
                .containsEntry(JsonDeserializer.VALUE_DEFAULT_TYPE, TradeCompletedEvent.class.getName())
                .containsEntry(JsonDeserializer.TRUSTED_PACKAGES, "com.muscat.*")
                .containsEntry(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
    }

    @Test
    @DisplayName("컨슈머 팩토리 설정으로 만든 역직렬화기가 모르는 필드가 있어도 지정한 타입으로 읽는다")
    void jsonConsumerFactory_ReadsGivenType() {
        ConsumerFactory<String, TradeCompletedEvent> factory =
                KafkaFactories.jsonConsumerFactory(BOOTSTRAP, "g", TradeCompletedEvent.class, new SimpleMeterRegistry());
        Map<String, Object> props = factory.getConfigurationProperties();
        byte[] json = """
                {"tradeId": 7, "accountId": 20, "addedLater": true}
                """.getBytes(StandardCharsets.UTF_8);

        // 컨슈머와 같은 방법으로 설정에서 생성
        try (Deserializer<?> deserializer = new ConsumerConfig(props)
                .getConfiguredInstance(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, Deserializer.class)) {
            deserializer.configure(props, false);

            assertThat(deserializer.deserialize("trading.trade.completed", json))
                    .isInstanceOfSatisfying(TradeCompletedEvent.class, event -> {
                        assertThat(event.getTradeId()).isEqualTo(7L);
                        assertThat(event.getAccountId()).isEqualTo(20L);
                    });
        }
    }

    @Test
    @DisplayName("컨테이너는 수동 커밋 · 지정한 동시성 · 넘긴 에러 핸들러로 만든다")
    void manualAckContainer() {
        ConsumerFactory<String, TradeCompletedEvent> consumerFactory =
                KafkaFactories.jsonConsumerFactory(BOOTSTRAP, "g", TradeCompletedEvent.class, new SimpleMeterRegistry());
        CommonErrorHandler errorHandler = new DefaultErrorHandler();

        ConcurrentKafkaListenerContainerFactory<String, TradeCompletedEvent> factory =
                KafkaFactories.manualAckContainer(consumerFactory, 3, errorHandler);
        ConcurrentMessageListenerContainer<String, TradeCompletedEvent> container =
                factory.createContainer("trading.trade.completed");

        assertThat(factory.getConsumerFactory()).isSameAs(consumerFactory);
        assertThat(container.getContainerProperties().getAckMode()).isEqualTo(ContainerProperties.AckMode.MANUAL);
        assertThat(container.getConcurrency()).isEqualTo(3);
        assertThat(container.getCommonErrorHandler()).isSameAs(errorHandler);
    }

    @Test
    @DisplayName("DLT 템플릿은 문자열 키 · JSON 값에 acks=all 로 보낸다")
    void dltTemplate() {
        KafkaTemplate<String, Object> template = KafkaFactories.dltTemplate(BOOTSTRAP, new SimpleMeterRegistry());

        assertThat(template.getProducerFactory().getConfigurationProperties())
                .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP)
                .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
                .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class)
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all");
    }

    @Test
    @DisplayName("컨슈머 팩토리로 만든 컨슈머는 지연 지표를 Micrometer 로 낸다")
    void consumerFactory_BindsClientMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ConsumerFactory<String, TradeCompletedEvent> factory = KafkaFactories.consumerFactory(
                KafkaFactories.jsonConsumerProps(LOCAL_BOOTSTRAP, "g", TradeCompletedEvent.class), meterRegistry);

        try (Consumer<String, TradeCompletedEvent> consumer = factory.createConsumer()) {
            assertThat(meterRegistry.find("kafka.consumer.fetch.manager.records.lag.max").meters()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("JSON 컨슈머 팩토리로 만든 컨슈머도 지연 지표를 Micrometer 로 낸다")
    void jsonConsumerFactory_BindsClientMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ConsumerFactory<String, TradeCompletedEvent> factory =
                KafkaFactories.jsonConsumerFactory(LOCAL_BOOTSTRAP, "g", TradeCompletedEvent.class, meterRegistry);

        try (Consumer<String, TradeCompletedEvent> consumer = factory.createConsumer()) {
            assertThat(meterRegistry.find("kafka.consumer.fetch.manager.records.lag.max").meters()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("프로듀서 팩토리로 만든 프로듀서는 전송 지표를 Micrometer 로 낸다")
    void producerFactory_BindsClientMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        DefaultKafkaProducerFactory<String, Object> factory =
                KafkaFactories.producerFactory(KafkaFactories.producerProps(LOCAL_BOOTSTRAP), meterRegistry);

        assertProducerMetricsBound(factory, meterRegistry);
    }

    @Test
    @DisplayName("DLT 템플릿의 프로듀서도 전송 지표를 Micrometer 로 낸다")
    void dltTemplate_BindsClientMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        KafkaTemplate<String, Object> template = KafkaFactories.dltTemplate(LOCAL_BOOTSTRAP, meterRegistry);

        assertProducerMetricsBound((DefaultKafkaProducerFactory<String, Object>) template.getProducerFactory(),
                meterRegistry);
    }

    // 프로듀서를 하나 만들어 지표를 보고, 팩토리를 닫아 실제 프로듀서도 정리
    private static void assertProducerMetricsBound(
            DefaultKafkaProducerFactory<String, Object> factory, SimpleMeterRegistry meterRegistry) {
        factory.setPhysicalCloseTimeout(0);
        try {
            Producer<String, Object> producer = factory.createProducer();
            assertThat(producer).isNotNull();
            assertThat(meterRegistry.find("kafka.producer.record.error.total").meters()).isNotEmpty();
        } finally {
            factory.destroy();
        }
    }

    @Test
    @DisplayName("다시 해도 안 되는 메시지는 <토픽>.DLT 로 같은 키로 넘기고 kafka.dlt.records 를 올린다")
    void deadLetterErrorHandler_PublishesToDltAndCounts() {
        MockProducer<String, Object> producer =
                new MockProducer<>(true, new StringSerializer(), new JsonSerializer<>());
        KafkaTemplate<String, Object> dlt = new KafkaTemplate<>(new MockProducerFactory<>(() -> producer));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CommonErrorHandler errorHandler = KafkaFactories.deadLetterErrorHandler(dlt, meterRegistry);
        ConsumerRecord<String, Object> record = new ConsumerRecord<>("user.account.deleted", 0, 5L, "user-1", "{}");

        // 역직렬화 실패는 다시 해도 같아 재시도 없이 넘김
        errorHandler.handleOne(new DeserializationException("bad json", new byte[0], false, null),
                record, null, mock(MessageListenerContainer.class));

        assertThat(producer.history()).extracting(ProducerRecord::topic, ProducerRecord::key)
                .containsExactly(tuple("user.account.deleted.DLT", "user-1"));
        assertThat(meterRegistry.get("kafka.dlt.records").counter().count()).isEqualTo(1.0);
    }
}
