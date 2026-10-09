package com.muscat.backtest.domain.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SimulationRequest 평가일")
class SimulationRequestTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

  private SimulationRequest withSaleDate(LocalDate saleDate) {
    return SimulationRequest.builder()
      .symbol("AAPL")
      .purchaseDate(LocalDate.of(2024, 1, 15))
      .saleDate(saleDate)
      .build();
  }

  @Test
  @DisplayName("매도일이 과거면 그날")
  void pastSaleDate() {
    assertThat(withSaleDate(LocalDate.of(2024, 6, 14)).valuationDate(TODAY))
      .isEqualTo(LocalDate.of(2024, 6, 14));
  }

  @Test
  @DisplayName("매도일이 없거나 오늘 · 미래면 오늘")
  void noOrFutureSaleDate() {
    assertThat(withSaleDate(null).valuationDate(TODAY)).isEqualTo(TODAY);
    assertThat(withSaleDate(TODAY).valuationDate(TODAY)).isEqualTo(TODAY);
    assertThat(withSaleDate(LocalDate.of(2030, 1, 1)).valuationDate(TODAY)).isEqualTo(TODAY);
  }
}
