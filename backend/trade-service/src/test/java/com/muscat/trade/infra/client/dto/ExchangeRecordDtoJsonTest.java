package com.muscat.trade.infra.client.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

// Spring 기본 ObjectMapper 와 같은 설정으로 user 응답 JSON 읽기
@DisplayName("환전 기록 JSON")
class ExchangeRecordDtoJsonTest {

  private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

  @Test
  @DisplayName("user 환전 기록 응답의 칸이 이름 그대로 읽힌다")
  void readsUserResponse() throws Exception {
    String json = """
      [{"accountId":3,"exchangedAt":"2024-09-02T10:15:30","fromCurrency":"KRW","toCurrency":"USD",
        "originalAmount":1300000.00,"exchangedAmount":1000.00,"exchangeRate":1300.000000}]
      """;

    List<ExchangeRecordDto> records = List.of(objectMapper.readValue(json, ExchangeRecordDto[].class));

    assertThat(records).singleElement().satisfies(record -> {
      assertThat(record.accountId()).isEqualTo(3L);
      assertThat(record.exchangedAt()).isEqualTo(LocalDateTime.of(2024, 9, 2, 10, 15, 30));
      assertThat(record.fromCurrency()).isEqualTo("KRW");
      assertThat(record.toCurrency()).isEqualTo("USD");
      assertThat(record.originalAmount()).isEqualByComparingTo(new BigDecimal("1300000.00"));
      assertThat(record.exchangedAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
      assertThat(record.exchangeRate()).isEqualByComparingTo(new BigDecimal("1300.000000"));
    });
  }
}
