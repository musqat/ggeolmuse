package com.muscat.messaging.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.MicrometerConsumerListener;
import org.springframework.kafka.core.MicrometerProducerListener;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

// 서비스 Kafka 프로듀서 · 컨슈머 · DLT 설정 공통 값
@Slf4j
public final class KafkaFactories {

    // 첫 전달이 실패하면 1초 간격으로 세 번 더 보냄
    private static final long RETRY_INTERVAL_MS = 1000L;
    private static final long MAX_RETRIES = 3L;

    private KafkaFactories() {
    }

    // 문자열 키 · JSON 값, acks=all · 재시도 3 · 멱등 전송(압축 · 배치 값은 서비스가 더함)
    public static Map<String, Object> producerProps(String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return props;
    }

    // 수동 커밋 · earliest. 값은 타입 헤더 없이 type 으로 읽고, 못 읽으면 리스너 대신 에러 핸들러로
    public static Map<String, Object> jsonConsumerProps(String bootstrapServers, String groupId, Class<?> type) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, type.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.muscat.*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return props;
    }

    // 클라이언트 지표(소비자 지연 등)를 Micrometer 로 내는 컨슈머 팩토리
    public static <K, V> DefaultKafkaConsumerFactory<K, V> consumerFactory(
            Map<String, Object> props, MeterRegistry meterRegistry) {
        DefaultKafkaConsumerFactory<K, V> factory = new DefaultKafkaConsumerFactory<>(props);
        factory.addListener(new MicrometerConsumerListener<>(meterRegistry));
        return factory;
    }

    // 클라이언트 지표(전송 실패 · 지연 등)를 Micrometer 로 내는 프로듀서 팩토리
    public static <K, V> DefaultKafkaProducerFactory<K, V> producerFactory(
            Map<String, Object> props, MeterRegistry meterRegistry) {
        DefaultKafkaProducerFactory<K, V> factory = new DefaultKafkaProducerFactory<>(props);
        factory.addListener(new MicrometerProducerListener<>(meterRegistry));
        return factory;
    }

    public static <V> ConsumerFactory<String, V> jsonConsumerFactory(
            String bootstrapServers, String groupId, Class<V> type, MeterRegistry meterRegistry) {
        return consumerFactory(jsonConsumerProps(bootstrapServers, groupId, type), meterRegistry);
    }

    // 처리가 끝난 뒤 리스너가 직접 커밋하는 컨테이너
    public static <V> ConcurrentKafkaListenerContainerFactory<String, V> manualAckContainer(
            ConsumerFactory<String, V> consumerFactory, int concurrency, CommonErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, V> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setConcurrency(concurrency);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    // 재시도로 못 살린 메시지를 .DLT 토픽에 넣는 템플릿
    public static KafkaTemplate<String, Object> dltTemplate(String bootstrapServers, MeterRegistry meterRegistry) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaTemplate<>(producerFactory(props, meterRegistry));
    }

    // 첫 전달까지 최대 네 번(다 실패하면 <토픽>.DLT 로 넘기고 kafka.dlt.records 올림)
    public static CommonErrorHandler deadLetterErrorHandler(
            KafkaTemplate<String, Object> dltTemplate, MeterRegistry meterRegistry) {
        // 파티션 -1 은 카프카가 고름(DLT 파티션 수가 원본보다 적어도 됨)
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dltTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1));

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                new CountingDeadLetterRecoverer(recoverer, meterRegistry),
                new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));

        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("전달 {}/{} 실패: topic={}, partition={}, offset={}, error={}",
                        deliveryAttempt, MAX_RETRIES + 1, record.topic(), record.partition(),
                        record.offset(), ex.getMessage()));

        return errorHandler;
    }
}
