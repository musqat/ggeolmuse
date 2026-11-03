package com.muscat.backtest.infra.client.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("환율 응답")
class FxRateDtoTest {

  @Test
  @DisplayName("시장 응답 · 직접 넣은 환율은 기본값 표시가 없다")
  void marketRateIsNotDefault() throws Exception {
    FxRateDto parsed = new ObjectMapper().findAndRegisterModules()
      .readValue("{\"date\":\"2024-03-04\",\"rate\":1340}", FxRateDto.class);

    assertThat(parsed.rate()).isEqualByComparingTo("1340");
    assertThat(parsed.defaulted()).isFalse();
    assertThat(new FxRateDto(LocalDate.of(2024, 3, 4), new BigDecimal("1340")).defaulted()).isFalse();
  }
}
