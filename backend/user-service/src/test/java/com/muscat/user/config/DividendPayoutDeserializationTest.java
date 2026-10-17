package com.muscat.user.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.messaging.event.DividendPayoutEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("배당 몫 이벤트 역직렬화")
class DividendPayoutDeserializationTest {

  @Test
  @DisplayName("모르는 필드가 실린 배당 몫 이벤트도 읽는다")
  void ignoresUnknownField() {
    KafkaConsumerConfig config = new KafkaConsumerConfig(new SimpleMeterRegistry());
    ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
    ReflectionTestUtils.setField(config, "applicationName", "user-service");
    Map<String, Object> props = config.dividendPayoutEventConsumerFactory().getConfigurationProperties();
    byte[] json = """
      {"accountId": 20, "symbol": "AAPL", "exDate": "2024-02-09", "netAmount": 2.03, "addedLater": true}
      """.getBytes(StandardCharsets.UTF_8);

    try (Deserializer<?> deserializer = new ConsumerConfig(props)
      .getConfiguredInstance(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, Deserializer.class)) {
      deserializer.configure(props, false);

      assertThat(deserializer.deserialize("trading.dividend.payout", json))
        .isInstanceOfSatisfying(DividendPayoutEvent.class, event -> {
          assertThat(event.getAccountId()).isEqualTo(20L);
          assertThat(event.getExDate()).isEqualTo("2024-02-09");
          assertThat(event.getNetAmount()).isEqualByComparingTo("2.03");
        });
    }
  }
}
