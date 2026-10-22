package com.muscat.user.config;

import com.muscat.messaging.event.AccountBalanceUpdatedEvent;
import com.muscat.messaging.event.AccountDeletedEvent;
import com.muscat.messaging.event.EmailSendEvent;
import com.muscat.messaging.event.TradeRejectedEvent;
import com.muscat.messaging.event.TradeSettledEvent;
import com.muscat.messaging.event.UserLoginFailedEvent;
import com.muscat.messaging.event.UserLoginSuccessEvent;
import com.muscat.messaging.kafka.KafkaFactories;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

// User 서비스 Kafka 프로듀서 설정
@Configuration
public class KafkaProducerConfig {

  @Value("${spring.kafka.bootstrap-servers}")
  private String bootstrapServers;

  @Bean
  public ProducerFactory<String, EmailSendEvent> emailEventProducerFactory() {
    return new DefaultKafkaProducerFactory<>(gzipProps());
  }

  @Bean
  public KafkaTemplate<String, EmailSendEvent> emailEventKafkaTemplate() {
    return new KafkaTemplate<>(emailEventProducerFactory());
  }

  @Bean
  public ProducerFactory<String, AccountBalanceUpdatedEvent> accountBalanceUpdatedEventProducerFactory() {
    return new DefaultKafkaProducerFactory<>(gzipProps());
  }

  @Bean
  public KafkaTemplate<String, AccountBalanceUpdatedEvent> accountBalanceUpdatedKafkaTemplate() {
    return new KafkaTemplate<>(accountBalanceUpdatedEventProducerFactory());
  }

  @Bean
  public ProducerFactory<String, UserLoginSuccessEvent> loginSuccessEventProducerFactory() {
    return new DefaultKafkaProducerFactory<>(gzipProps());
  }

  @Bean
  public KafkaTemplate<String, UserLoginSuccessEvent> loginSuccessKafkaTemplate() {
    return new KafkaTemplate<>(loginSuccessEventProducerFactory());
  }

  @Bean
  public ProducerFactory<String, UserLoginFailedEvent> loginFailedEventProducerFactory() {
    return new DefaultKafkaProducerFactory<>(gzipProps());
  }

  @Bean
  public KafkaTemplate<String, UserLoginFailedEvent> loginFailedKafkaTemplate() {
    return new KafkaTemplate<>(loginFailedEventProducerFactory());
  }

  @Bean
  public ProducerFactory<String, AccountDeletedEvent> accountDeletedEventProducerFactory() {
    return new DefaultKafkaProducerFactory<>(gzipProps());
  }

  @Bean
  public KafkaTemplate<String, AccountDeletedEvent> accountDeletedKafkaTemplate() {
    return new KafkaTemplate<>(accountDeletedEventProducerFactory());
  }

  @Bean
  public ProducerFactory<String, TradeRejectedEvent> tradeRejectedEventProducerFactory() {
    return new DefaultKafkaProducerFactory<>(fastFailProps());
  }

  @Bean
  public KafkaTemplate<String, TradeRejectedEvent> tradeRejectedKafkaTemplate() {
    return new KafkaTemplate<>(tradeRejectedEventProducerFactory());
  }

  @Bean
  public ProducerFactory<String, TradeSettledEvent> tradeSettledEventProducerFactory() {
    return new DefaultKafkaProducerFactory<>(fastFailProps());
  }

  @Bean
  public KafkaTemplate<String, TradeSettledEvent> tradeSettledKafkaTemplate() {
    return new KafkaTemplate<>(tradeSettledEventProducerFactory());
  }

  private Map<String, Object> gzipProps() {
    Map<String, Object> props = KafkaFactories.producerProps(bootstrapServers);
    props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");
    return props;
  }

  // 브로커를 못 찾으면 5초 안에 실패 (기본 60초)
  private Map<String, Object> fastFailProps() {
    Map<String, Object> props = KafkaFactories.producerProps(bootstrapServers);
    props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
    return props;
  }
}
