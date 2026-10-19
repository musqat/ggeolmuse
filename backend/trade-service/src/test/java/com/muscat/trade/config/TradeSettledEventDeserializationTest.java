package com.muscat.trade.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.messaging.event.TradeSettledEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// 모르는 칸에 막히면 리스너를 못 불러 첫 시도에 DLT 로 감
@DisplayName("정산됨 이벤트 역직렬화")
class TradeSettledEventDeserializationTest {

  @Test
  @DisplayName("모르는 칸이 실린 정산됨 이벤트도 읽는다")
  void ignoresUnknownField() {
    KafkaConsumerConfig config = new KafkaConsumerConfig(new SimpleMeterRegistry());
    ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
    ReflectionTestUtils.setField(config, "applicationName", "trade-service");
    Map<String, Object> props = config.tradeSettledEventConsumerFactory().getConfigurationProperties();
    byte[] json = """
      {"tradeId": 7, "accountId": 20, "userId": "user-1", "settledBy": "user-service"}
      """.getBytes(StandardCharsets.UTF_8);

    // 컨슈머와 같은 방법으로 설정에서 생성
    try (Deserializer<?> deserializer = new ConsumerConfig(props)
      .getConfiguredInstance(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, Deserializer.class)) {
      deserializer.configure(props, false);

      assertThat(deserializer.deserialize("user.trade.settled", json))
        .isInstanceOfSatisfying(TradeSettledEvent.class, event -> {
          assertThat(event.getTradeId()).isEqualTo(7L);
          assertThat(event.getAccountId()).isEqualTo(20L);
        });
    }
  }
}
