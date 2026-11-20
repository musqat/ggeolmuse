package com.muscat.backtest.config;

import com.muscat.messaging.event.UserDeletedEvent;
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

// 백테스트 서비스 Kafka 소비 설정(사용자 삭제 이벤트)
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
  public ConsumerFactory<String, UserDeletedEvent> userDeletedEventConsumerFactory() {
    return KafkaFactories.jsonConsumerFactory(bootstrapServers, applicationName + "-user-deleted-consumer",
      UserDeletedEvent.class, meterRegistry);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, UserDeletedEvent>
  userDeletedEventKafkaListenerContainerFactory() {
    return KafkaFactories.manualAckContainer(userDeletedEventConsumerFactory(), 1,
      KafkaFactories.deadLetterErrorHandler(dltKafkaTemplate(), meterRegistry));
  }

  // 재시도 뒤에도 실패한 메시지를 .DLT 토픽에 넣는 템플릿
  @Bean
  public KafkaTemplate<String, Object> dltKafkaTemplate() {
    return KafkaFactories.dltTemplate(bootstrapServers, meterRegistry);
  }
}
