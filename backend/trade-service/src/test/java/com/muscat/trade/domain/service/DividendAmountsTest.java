package com.muscat.trade.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("배당 금액 계산")
class DividendAmountsTest {

  @ParameterizedTest(name = "주당 {0} × {1}주 → 세전 {2} · 세금 {3} · 세후 {4}")
  @CsvSource({
    "0.24, 10, 2.40, 0.37, 2.03",
    "0.25, 10, 2.50, 0.39, 2.11",
    "0.26, 10, 2.60, 0.40, 2.20",
    "0.25, 5, 1.25, 0.19, 1.06",
    "0.25, 0, 0.00, 0.00, 0.00"
  })
  @DisplayName("세전은 주당 배당 × 수량, 세금은 세전의 15.4% 를 센트에서 반올림, 세후는 그 차이")
  void of_RoundsEachStepToCents(String perShare, String shares, String gross, String tax, String net) {
    DividendAmounts amounts = DividendAmounts.of(new BigDecimal(perShare), new BigDecimal(shares));

    assertThat(amounts.grossAmount()).isEqualByComparingTo(gross);
    assertThat(amounts.taxAmount()).isEqualByComparingTo(tax);
    assertThat(amounts.netAmount()).isEqualByComparingTo(net);
  }

  @Test
  @DisplayName("주당 배당이 소수 여섯째 자리를 넘으면 여섯째 자리로 반올림하고 짧은 값은 그대로 둔다")
  void of_RoundsPerShareToSixDecimals() {
    assertThat(DividendAmounts.of(new BigDecimal("0.24666667"), BigDecimal.TEN).dividendPerShare())
      .isEqualTo(new BigDecimal("0.246667"));
    assertThat(DividendAmounts.of(new BigDecimal("0.25"), BigDecimal.TEN).dividendPerShare())
      .isEqualTo(new BigDecimal("0.25"));
  }
}
