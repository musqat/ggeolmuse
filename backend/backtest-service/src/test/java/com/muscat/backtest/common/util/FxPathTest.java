package com.muscat.backtest.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FxPath 날짜별 환율")
class FxPathTest {

  private static final LocalDate JAN2 = LocalDate.of(2024, 1, 2);
  private static final LocalDate JAN4 = LocalDate.of(2024, 1, 4);

  @Test
  @DisplayName("시장 환율은 그 날짜 이하 가장 늦은 기록, 첫 기록 전 날은 첫 기록으로 채운다")
  void marketFillsBeforeFirstRecord() {
    Function<LocalDate, BigDecimal> fx = FxPath.market(
      Map.of(JAN2, new BigDecimal("1300"), JAN4, new BigDecimal("1320")), new BigDecimal("1350"));

    assertThat(fx.apply(LocalDate.of(2024, 1, 1))).isEqualByComparingTo("1300");
    assertThat(fx.apply(LocalDate.of(2024, 1, 3))).isEqualByComparingTo("1300");
    assertThat(fx.apply(LocalDate.of(2024, 1, 5))).isEqualByComparingTo("1320");
  }

  @Test
  @DisplayName("시장 환율 기록이 하나도 없으면 주어진 환율 하나로 센다")
  void marketWithoutRecords() {
    Function<LocalDate, BigDecimal> fx = FxPath.market(Map.of(), new BigDecimal("1300"));

    assertThat(fx.apply(JAN2)).isEqualByComparingTo("1300");
  }

  @Test
  @DisplayName("수동 환율은 시작일 환율에서 평가일 환율까지 날짜에 비례해 잇는다")
  void interpolatedBetweenManualRates() {
    LocalDate start = LocalDate.of(2024, 1, 1);
    LocalDate end = LocalDate.of(2024, 1, 11);

    Function<LocalDate, BigDecimal> fx = FxPath.interpolated(start, new BigDecimal("1300"), end, new BigDecimal("1400"));

    assertThat(fx.apply(start)).isEqualByComparingTo("1300");
    assertThat(fx.apply(LocalDate.of(2024, 1, 6))).isEqualByComparingTo("1350");
    assertThat(fx.apply(end)).isEqualByComparingTo("1400");
    assertThat(fx.apply(LocalDate.of(2023, 12, 1))).isEqualByComparingTo("1300");
    assertThat(fx.apply(LocalDate.of(2024, 2, 1))).isEqualByComparingTo("1400");
  }

  @Test
  @DisplayName("시작일과 평가일이 같으면 시작일 환율 하나로 센다")
  void interpolatedSameDay() {
    Function<LocalDate, BigDecimal> fx = FxPath.interpolated(JAN2, new BigDecimal("1300"), JAN2, new BigDecimal("1400"));

    assertThat(fx.apply(JAN2)).isEqualByComparingTo("1300");
  }
}
