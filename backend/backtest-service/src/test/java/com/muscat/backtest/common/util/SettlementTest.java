package com.muscat.backtest.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Settlement 판 거래의 결제일(주말만 건너뛴 평일 기준)")
class SettlementTest {

  @Test
  @DisplayName("2024-05-28 부터는 다음 평일이라 2025-12-31 에 판 거래의 결제일은 휴장일 2026-01-01, 2025-12-30 에 판 거래는 그해 12월 31일이다")
  void tPlusOneCrossesYear() {
    assertThat(Settlement.date(LocalDate.of(2025, 12, 31))).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(Settlement.date(LocalDate.of(2025, 12, 30))).isEqualTo(LocalDate.of(2025, 12, 31));
  }

  @Test
  @DisplayName("2024-05-28 전은 둘째 평일이라 2023-12-28 에 판 거래의 결제일은 휴장일 2024-01-01, 2023-12-27 에 판 거래는 그해 12월 29일이다")
  void tPlusTwoBeforeMay2024() {
    assertThat(Settlement.date(LocalDate.of(2023, 12, 28))).isEqualTo(LocalDate.of(2024, 1, 1));
    assertThat(Settlement.date(LocalDate.of(2023, 12, 27))).isEqualTo(LocalDate.of(2023, 12, 29));
  }

  @Test
  @DisplayName("2024-05-24 금요일에 판 거래의 결제일은 휴장일 5월 27일을 세어 둘째 평일 5월 28일, 2024-05-28 에 판 거래는 다음 평일 5월 29일이다")
  void switchesOnMay28() {
    assertThat(Settlement.date(LocalDate.of(2024, 5, 24))).isEqualTo(LocalDate.of(2024, 5, 28));
    assertThat(Settlement.date(LocalDate.of(2024, 5, 28))).isEqualTo(LocalDate.of(2024, 5, 29));
  }

  @Test
  @DisplayName("주말은 건너뛰어 금요일에 판 거래의 결제일은 다음 월요일이다")
  void skipsWeekend() {
    assertThat(Settlement.date(LocalDate.of(2025, 6, 13))).isEqualTo(LocalDate.of(2025, 6, 16));
  }
}
