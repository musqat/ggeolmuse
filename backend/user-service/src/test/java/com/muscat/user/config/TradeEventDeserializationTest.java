package com.muscat.user.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.messaging.event.TradeCompletedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// 모르는 필드에 막히면 리스너를 못 불러 첫 시도에 DLT 로 감
@DisplayName("체결 이벤트 역직렬화")
class TradeEventDeserializationTest {

  @Test
  @DisplayName("모르는 필드가 실린 체결 이벤트도 읽는다")
  void ignoresUnknownField() {
    KafkaConsumerConfig config = new KafkaConsumerConfig(new SimpleMeterRegistry());
    ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
    ReflectionTestUtils.setField(config, "applicationName", "user-service");
    Map<String, Object> props = config.tradeEventConsumerFactory().getConfigurationProperties();
    byte[] json = """
      {"tradeId": 7, "accountId": 20, "tradeType": "BUY", "priceType": "MARKET"}
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
}
