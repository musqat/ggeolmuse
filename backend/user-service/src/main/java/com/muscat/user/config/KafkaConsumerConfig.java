package com.muscat.user.config;

import com.muscat.messaging.event.AccountDepositCompletedEvent;
import com.muscat.messaging.event.AccountWithdrawalCompletedEvent;
import com.muscat.messaging.event.DividendReceivedEvent;
import com.muscat.messaging.event.EmailSendEvent;
import com.muscat.messaging.event.TradeCancelledEvent;
import com.muscat.messaging.event.TradeCompletedEvent;
import java.util.HashMap;
import java.util.Map;
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

/**
 * User Service Kafka Consumer 설정
 * TradeCompletedEvent, EmailSendEvent
 */
@Slf4j
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

  // 첫 전달이 실패하면 1초 간격으로 세 번 더 보낸다
  private static final long RETRY_INTERVAL_MS = 1000L;
  private static final long MAX_RETRIES = 3L;

  @Value("${spring.kafka.bootstrap-servers}")
  private String bootstrapServers;

  @Value("${spring.application.name}")
  private String applicationName;

  @Bean
  public ConsumerFactory<String, TradeCompletedEvent> tradeEventConsumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, applicationName + "-trade-consumer");

    // 수동 커밋 모드 (메시지 처리 성공시에만 커밋)
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

    // 컨슈머 그룹 최초 실행시 earliest부터 읽기
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    // Deserializer 설정
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());

    // JSON Deserializer 추가 설정
    props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, TradeCompletedEvent.class.getName());
    props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.muscat.*");
    props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, TradeCompletedEvent>
  tradeEventKafkaListenerContainerFactory() {

    ConcurrentKafkaListenerContainerFactory<String, TradeCompletedEvent> factory =
      new ConcurrentKafkaListenerContainerFactory<>();

    factory.setConsumerFactory(tradeEventConsumerFactory());

    // 수동 커밋 모드 설정
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

    // 동시성 레벨 (병렬 Consumer 스레드 수)
    factory.setConcurrency(3);

    // Consumer 재시도 설정은 Config Server의 공통 설정 사용
    // (spring.kafka.consumer 설정)

    // 공통 에러 핸들러 설정 (DLQ 포함)
    factory.setCommonErrorHandler(kafkaErrorHandler());

    return factory;
  }

  @Bean
  public ConsumerFactory<String, TradeCancelledEvent> tradeCancelledEventConsumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, applicationName + "-trade-cancel-consumer");

    // 수동 커밋 모드 (메시지 처리 성공시에만 커밋)
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

    // 컨슈머 그룹 최초 실행시 earliest부터 읽기
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    // Deserializer 설정
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());

    // JSON Deserializer 추가 설정
    props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, TradeCancelledEvent.class.getName());
    props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.muscat.*");
    props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, TradeCancelledEvent>
  tradeCancelledEventKafkaListenerContainerFactory() {

    ConcurrentKafkaListenerContainerFactory<String, TradeCancelledEvent> factory =
      new ConcurrentKafkaListenerContainerFactory<>();

    factory.setConsumerFactory(tradeCancelledEventConsumerFactory());

    // 수동 커밋 모드 설정
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

    // 동시성 레벨 (병렬 Consumer 스레드 수)
    factory.setConcurrency(3);

    // 공통 에러 핸들러 설정 (DLQ 포함)
    factory.setCommonErrorHandler(kafkaErrorHandler());

    return factory;
  }

  @Bean
  public ConsumerFactory<String, DividendReceivedEvent> dividendReceivedEventConsumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, applicationName + "-dividend-received-consumer");

    // 수동 커밋 모드 (메시지 처리 성공시에만 커밋)
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

    // 컨슈머 그룹 최초 실행시 earliest부터 읽기
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    // Deserializer 설정
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());

    // JSON Deserializer 추가 설정
    props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, DividendReceivedEvent.class.getName());
    props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.muscat.*");
    props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, DividendReceivedEvent>
  dividendReceivedEventKafkaListenerContainerFactory() {

    ConcurrentKafkaListenerContainerFactory<String, DividendReceivedEvent> factory =
      new ConcurrentKafkaListenerContainerFactory<>();

    factory.setConsumerFactory(dividendReceivedEventConsumerFactory());

    // 수동 커밋 모드 설정
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

    // 동시성 레벨 (병렬 Consumer 스레드 수)
    factory.setConcurrency(3);

    // 공통 에러 핸들러 설정 (DLQ 포함)
    factory.setCommonErrorHandler(kafkaErrorHandler());

    return factory;
  }

  @Bean
  public ConsumerFactory<String, EmailSendEvent> emailEventConsumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, applicationName + "-email-consumer");

    // 수동 커밋 모드 (메시지 처리 성공시에만 커밋)
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

    // 컨슈머 그룹 최초 실행시 earliest부터 읽기
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    // Deserializer 설정
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());

    // JSON Deserializer 추가 설정
    props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, EmailSendEvent.class.getName());
    props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.muscat.*");
    props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, EmailSendEvent>
  emailEventKafkaListenerContainerFactory() {

    ConcurrentKafkaListenerContainerFactory<String, EmailSendEvent> factory =
      new ConcurrentKafkaListenerContainerFactory<>();

    factory.setConsumerFactory(emailEventConsumerFactory());

    // 수동 커밋 모드 설정
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

    // 동시성 레벨 (병렬 Consumer 스레드 수)
    factory.setConcurrency(3);

    // Consumer 재시도 설정은 Config Server의 공통 설정 사용
    // (spring.kafka.consumer 설정)

    // 공통 에러 핸들러 설정 (DLQ 포함)
    factory.setCommonErrorHandler(kafkaErrorHandler());

    return factory;
  }

  @Bean
  public ConsumerFactory<String, AccountDepositCompletedEvent> depositCompletedEventConsumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, applicationName + "-deposit-consumer");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    // Deserializer 설정
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());

    // JSON Deserializer 추가 설정
    props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, AccountDepositCompletedEvent.class.getName());
    props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.muscat.*");
    props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, AccountDepositCompletedEvent>
  depositCompletedEventKafkaListenerContainerFactory() {

    ConcurrentKafkaListenerContainerFactory<String, AccountDepositCompletedEvent> factory =
      new ConcurrentKafkaListenerContainerFactory<>();

    factory.setConsumerFactory(depositCompletedEventConsumerFactory());
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
    factory.setConcurrency(3);
    factory.setCommonErrorHandler(kafkaErrorHandler());

    return factory;
  }

  @Bean
  public ConsumerFactory<String, AccountWithdrawalCompletedEvent> withdrawalCompletedEventConsumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, applicationName + "-withdrawal-consumer");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    // Deserializer 설정
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());

    // JSON Deserializer 추가 설정
    props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, AccountWithdrawalCompletedEvent.class.getName());
    props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.muscat.*");
    props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, AccountWithdrawalCompletedEvent>
  withdrawalCompletedEventKafkaListenerContainerFactory() {

    ConcurrentKafkaListenerContainerFactory<String, AccountWithdrawalCompletedEvent> factory =
      new ConcurrentKafkaListenerContainerFactory<>();

    factory.setConsumerFactory(withdrawalCompletedEventConsumerFactory());
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
    factory.setConcurrency(3);
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
   * 첫 전달까지 넣어 최대 네 번 보낸다. 다 실패하면 토픽 이름에 .DLT 를 붙인 곳으로 보낸다.
   */
  private CommonErrorHandler kafkaErrorHandler() {
    // 파티션을 -1 로 두면 카프카가 고른다. DLT 파티션 수가 원본보다 적어도 된다
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
      dltKafkaTemplate(),
      (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1));

    DefaultErrorHandler errorHandler =
      new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));

    errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
      log.warn("전달 {}/{} 실패: topic={}, partition={}, offset={}, error={}",
        deliveryAttempt, MAX_RETRIES + 1, record.topic(), record.partition(),
        record.offset(), ex.getMessage());
    });

    return errorHandler;
  }
}
