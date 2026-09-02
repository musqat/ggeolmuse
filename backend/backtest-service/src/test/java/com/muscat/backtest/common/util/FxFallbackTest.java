package com.muscat.backtest.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FxFallback 단위 테스트")
class FxFallbackTest {

  @Test
  @DisplayName("기본환율은 1300으로 단일화되어 있다")
  void defaultRateIs1300() {
    assertThat(FxFallback.DEFAULT_RATE).isEqualByComparingTo("1300");
  }

  @Test
  @DisplayName("defaultFxRate는 지정 날짜 + 1300 환율을 반환한다")
  void defaultFxRate() {
    var fx = FxFallback.defaultFxRate(LocalDate.of(2025, 6, 5));
    assertThat(fx.date()).isEqualTo(LocalDate.of(2025, 6, 5));
    assertThat(fx.rate()).isEqualByComparingTo("1300");
  }
}
