package com.muscat.trade.infra.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.trade.domain.event.DividendReconcileRequest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

// 프로듀서 · 컨슈머 설정과 같은 JsonSerializer · JsonDeserializer 로 확인
@DisplayName("배당 몫 재계산 요청 JSON")
class DividendReconcileRequestJsonTest {

  private static final String TOPIC = "trading.dividend.reconcile";

  @Test
  @DisplayName("다시 보내기와 넣은 배당락일이 보낸 그대로 읽힌다")
  void roundTrip_KeepsSettledExDates() {
    DividendReconcileRequest request = new DividendReconcileRequest("user-1", 20L, "AAPL", true,
      List.of(LocalDate.of(2025, 5, 12)));

    try (JsonSerializer<DividendReconcileRequest> serializer = new JsonSerializer<>();
         JsonDeserializer<DividendReconcileRequest> deserializer =
           new JsonDeserializer<>(DividendReconcileRequest.class, false)) {
      assertThat(deserializer.deserialize(TOPIC, serializer.serialize(TOPIC, request))).isEqualTo(request);
    }
  }

  @Test
  @DisplayName("다시 보내기 · 넣은 배당락일이 없는 요청은 보내지 않음 · 목록 없음으로 읽힌다")
  void missingFields_DefaultToNoResend() {
    byte[] json = "{\"userId\":\"user-1\",\"accountId\":20,\"symbol\":\"AAPL\"}".getBytes(StandardCharsets.UTF_8);

    try (JsonDeserializer<DividendReconcileRequest> deserializer =
           new JsonDeserializer<>(DividendReconcileRequest.class, false)) {
      assertThat(deserializer.deserialize(TOPIC, json))
        .isEqualTo(new DividendReconcileRequest("user-1", 20L, "AAPL", false, null));
    }
  }
}
