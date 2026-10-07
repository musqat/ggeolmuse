package com.muscat.user.config;

import com.muscat.messaging.event.AccountBalanceUpdatedEvent;
import com.muscat.messaging.event.AccountDeletedEvent;
import com.muscat.messaging.event.AccountDepositCompletedEvent;
import com.muscat.messaging.event.AccountWithdrawalCompletedEvent;
import com.muscat.messaging.event.EmailSendEvent;
import com.muscat.messaging.event.TradeRejectedEvent;
import com.muscat.messaging.event.UserLoginFailedEvent;
import com.muscat.messaging.event.UserLoginSuccessEvent;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * User Service Kafka Producer 설정
 * EmailSendEvent, TradeRejectedEvent
 */
@Configuration
public class KafkaProducerConfig {

  @Value("${spring.kafka.bootstrap-servers}")
  private String bootstrapServers;

  @Bean
  public ProducerFactory<String, EmailSendEvent> emailEventProducerFactory() {
    Map<String, Object> configProps = new HashMap<>();
    configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

    // Producer 안정성 설정
    configProps.put(ProducerConfig.ACKS_CONFIG, "all");
    configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
    configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

    // 성능 최적화
    configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip"); // Docker 호환성

    return new DefaultKafkaProducerFactory<>(configProps);
  }

  @Bean
  public KafkaTemplate<String, EmailSendEvent> emailEventKafkaTemplate() {
    return new KafkaTemplate<>(emailEventProducerFactory());
  }

  @Bean
  public ProducerFactory<String, AccountBalanceUpdatedEvent> accountBalanceUpdatedEventProducerFactory() {
    Map<String, Object> configProps = new HashMap<>();
    configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

    // Producer 안정성 설정
    configProps.put(ProducerConfig.ACKS_CONFIG, "all");
    configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
    configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

    // 성능 최적화
    configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");

    return new DefaultKafkaProducerFactory<>(configProps);
  }

  @Bean
  public KafkaTemplate<String, AccountBalanceUpdatedEvent> accountBalanceUpdatedKafkaTemplate() {
    return new KafkaTemplate<>(accountBalanceUpdatedEventProducerFactory());
  }

  @Bean
  public ProducerFactory<String, UserLoginSuccessEvent> loginSuccessEventProducerFactory() {
    Map<String, Object> configProps = new HashMap<>();
    configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

    // Producer 안정성 설정
    configProps.put(ProducerConfig.ACKS_CONFIG, "all");
    configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
    configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

    // 성능 최적화
    configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");

    return new DefaultKafkaProducerFactory<>(configProps);
  }

  @Bean
  public KafkaTemplate<String, UserLoginSuccessEvent> loginSuccessKafkaTemplate() {
    return new KafkaTemplate<>(loginSuccessEventProducerFactory());
  }

  @Bean
  public ProducerFactory<String, UserLoginFailedEvent> loginFailedEventProducerFactory() {
    Map<String, Object> configProps = new HashMap<>();
    configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

    // Producer 안정성 설정
    configProps.put(ProducerConfig.ACKS_CONFIG, "all");
    configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
    configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

    // 성능 최적화
    configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");

    return new DefaultKafkaProducerFactory<>(configProps);
  }

  @Bean
  public KafkaTemplate<String, UserLoginFailedEvent> loginFailedKafkaTemplate() {
    return new KafkaTemplate<>(loginFailedEventProducerFactory());
  }

  @Bean
  public ProducerFactory<String, AccountDepositCompletedEvent> depositCompletedEventProducerFactory() {
    Map<String, Object> configProps = new HashMap<>();
    configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

    // Producer 안정성 설정
    configProps.put(ProducerConfig.ACKS_CONFIG, "all");
    configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
    configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

    // 성능 최적화
    configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");

    return new DefaultKafkaProducerFactory<>(configProps);
  }

  @Bean
  public KafkaTemplate<String, AccountDepositCompletedEvent> depositCompletedKafkaTemplate() {
    return new KafkaTemplate<>(depositCompletedEventProducerFactory());
  }

  @Bean
  public ProducerFactory<String, AccountWithdrawalCompletedEvent> withdrawalCompletedEventProducerFactory() {
    Map<String, Object> configProps = new HashMap<>();
    configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

    // Producer 안정성 설정
    configProps.put(ProducerConfig.ACKS_CONFIG, "all");
    configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
    configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

    // 성능 최적화
    configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");

    return new DefaultKafkaProducerFactory<>(configProps);
  }

  @Bean
  public KafkaTemplate<String, AccountWithdrawalCompletedEvent> withdrawalCompletedKafkaTemplate() {
    return new KafkaTemplate<>(withdrawalCompletedEventProducerFactory());
  }

  @Bean
  public ProducerFactory<String, AccountDeletedEvent> accountDeletedEventProducerFactory() {
    Map<String, Object> configProps = new HashMap<>();
    configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

    // Producer 설정
    configProps.put(ProducerConfig.ACKS_CONFIG, "all");
    configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
    configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

    // 성능 최적화
    configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");

    return new DefaultKafkaProducerFactory<>(configProps);
  }

  @Bean
  public KafkaTemplate<String, AccountDeletedEvent> accountDeletedKafkaTemplate() {
    return new KafkaTemplate<>(accountDeletedEventProducerFactory());
  }

  @Bean
  public ProducerFactory<String, TradeRejectedEvent> tradeRejectedEventProducerFactory() {
    Map<String, Object> configProps = new HashMap<>();
    configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    configProps.put(ProducerConfig.ACKS_CONFIG, "all");
    configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
    configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    configProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000); // 브로커를 못 찾으면 5초 안에 실패 (기본 60초)
    return new DefaultKafkaProducerFactory<>(configProps);
  }

  @Bean
  public KafkaTemplate<String, TradeRejectedEvent> tradeRejectedKafkaTemplate() {
    return new KafkaTemplate<>(tradeRejectedEventProducerFactory());
  }
}
