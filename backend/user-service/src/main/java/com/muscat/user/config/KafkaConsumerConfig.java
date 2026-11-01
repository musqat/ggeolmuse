package com.muscat.user.config;

import com.muscat.messaging.event.AccountBalanceUpdatedEvent;
import com.muscat.messaging.event.DividendPayoutEvent;
import com.muscat.messaging.event.EmailSendEvent;
import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.messaging.event.UserLoginFailedEvent;
import com.muscat.messaging.event.UserLoginSuccessEvent;
import com.muscat.messaging.kafka.KafkaFactories;
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

// User 서비스 Kafka 컨슈머 설정
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
  public ConsumerFactory<String, TradeCompletedEvent> tradeEventConsumerFactory() {
    return consumerFactory(TradeCompletedEvent.class, "-trade-consumer");
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, TradeCompletedEvent>
  tradeEventKafkaListenerContainerFactory() {
    return KafkaFactories.manualAckContainer(tradeEventConsumerFactory(), 3, kafkaErrorHandler());
  }

  @Bean
  public ConsumerFactory<String, DividendPayoutEvent> dividendPayoutEventConsumerFactory() {
    return consumerFactory(DividendPayoutEvent.class, "-dividend-payout-consumer");
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, DividendPayoutEvent>
  dividendPayoutEventKafkaListenerContainerFactory() {
    return KafkaFactories.manualAckContainer(dividendPayoutEventConsumerFactory(), 3, kafkaErrorHandler());
  }

  @Bean
  public ConsumerFactory<String, EmailSendEvent> emailEventConsumerFactory() {
    return consumerFactory(EmailSendEvent.class, "-email-consumer");
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, EmailSendEvent>
  emailEventKafkaListenerContainerFactory() {
    return KafkaFactories.manualAckContainer(emailEventConsumerFactory(), 3, kafkaErrorHandler());
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, AccountBalanceUpdatedEvent>
  balanceUpdatedEventKafkaListenerContainerFactory() {
    return KafkaFactories.manualAckContainer(
      consumerFactory(AccountBalanceUpdatedEvent.class, "-balance-audit-consumer"), 1, kafkaErrorHandler());
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, UserLoginSuccessEvent>
  loginSuccessEventKafkaListenerContainerFactory() {
    return KafkaFactories.manualAckContainer(
      consumerFactory(UserLoginSuccessEvent.class, "-login-success-consumer"), 1, kafkaErrorHandler());
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, UserLoginFailedEvent>
  loginFailedEventKafkaListenerContainerFactory() {
    return KafkaFactories.manualAckContainer(
      consumerFactory(UserLoginFailedEvent.class, "-login-failed-consumer"), 1, kafkaErrorHandler());
  }

  /**
   * 재시도로 못 살린 메시지를 .DLT 토픽에 넣는 템플릿
   */
  @Bean
  public KafkaTemplate<String, Object> dltKafkaTemplate() {
    return KafkaFactories.dltTemplate(bootstrapServers, meterRegistry);
  }

  // 값 하나의 타입으로 역직렬화(리스너 @Payload 와 타입이 다르면 첫 시도에 DLT 로 감)
  private <T> ConsumerFactory<String, T> consumerFactory(Class<T> type, String groupSuffix) {
    return KafkaFactories.jsonConsumerFactory(bootstrapServers, applicationName + groupSuffix, type, meterRegistry);
  }

  private CommonErrorHandler kafkaErrorHandler() {
    return KafkaFactories.deadLetterErrorHandler(dltKafkaTemplate(), meterRegistry);
  }
}
