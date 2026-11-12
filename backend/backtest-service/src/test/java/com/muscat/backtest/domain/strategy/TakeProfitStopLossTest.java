package com.muscat.backtest.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TakeProfitStopLoss 평균 단가 기준 익절 · 손절 판정")
class TakeProfitStopLossTest {

  private static final BigDecimal TEN = new BigDecimal("10");
  private static final BigDecimal THOUSAND = new BigDecimal("1000");

  private static BigDecimal number(String value) {
    return value == null ? null : new BigDecimal(value);
  }

  // 시가 · 고가 · 저가 · 종가 일봉
  private static OHLCPriceDto candle(String open, String high, String low, String close) {
    return new OHLCPriceDto("AAA", LocalDate.of(2024, 3, 5), number(open), number(high), number(low), number(close),
      number(close), 1_000L, "USD", true);
  }

  // 판 값 · 익절인지 · 거래 이유(안 팔면 null)
  private static Tuple exit(TakeProfitStopLoss.Exit exit) {
    return exit == null ? null
      : tuple(exit.price().stripTrailingZeros().toPlainString(), exit.takeProfit(), exit.trigger());
  }

  @Test
  @DisplayName("비율을 둘 다 비우면 크게 오르내려도 팔지 않는다")
  void disabledWithoutRates() {
    TakeProfitStopLoss rule = TakeProfitStopLoss.of(null, null);

    assertThat(rule.check(candle("100", "200", "10", "100"), TEN, THOUSAND)).isNull();
  }

  @Test
  @DisplayName("고가가 평균 단가 × (1 + 익절 비율) 에 닿으면 그 값에, 시가부터 위면 시가에 익절한다")
  void takeProfit() {
    TakeProfitStopLoss rule = TakeProfitStopLoss.of(number("0.1"), null);

    assertThat(exit(rule.check(candle("101", "109.99", "50", "105"), TEN, THOUSAND))).isNull();
    assertThat(exit(rule.check(candle("101", "111", "100", "105"), TEN, THOUSAND)))
      .isEqualTo(tuple("110", true, "10.0%익절"));
    assertThat(exit(rule.check(candle("115", "116", "112", "114"), TEN, THOUSAND)))
      .isEqualTo(tuple("115", true, "15.0%익절"));
  }

  @Test
  @DisplayName("저가가 평균 단가 × (1 - 손절 비율) 에 닿으면 그 값에, 시가부터 아래면 시가에 손절한다")
  void stopLoss() {
    TakeProfitStopLoss rule = TakeProfitStopLoss.of(null, number("0.1"));

    assertThat(exit(rule.check(candle("95", "200", "90.01", "92"), TEN, THOUSAND))).isNull();
    assertThat(exit(rule.check(candle("95", "96", "89", "92"), TEN, THOUSAND)))
      .isEqualTo(tuple("90", false, "10.0%손절"));
    assertThat(exit(rule.check(candle("85", "86", "80", "82"), TEN, THOUSAND)))
      .isEqualTo(tuple("85", false, "15.0%손절"));
  }

  @Test
  @DisplayName("같은 날 두 기준가에 모두 닿으면 시가부터 익절 기준가 위일 때만 익절하고 아니면(시가가 없는 날 포함) 손절한다")
  void bothTouched() {
    TakeProfitStopLoss rule = TakeProfitStopLoss.of(number("0.05"), number("0.05"));

    assertThat(exit(rule.check(candle("100", "106", "94", "100"), TEN, THOUSAND)))
      .isEqualTo(tuple("95", false, "5.0%손절"));
    assertThat(exit(rule.check(candle("106", "107", "94", "100"), TEN, THOUSAND)))
      .isEqualTo(tuple("106", true, "6.0%익절"));
    assertThat(exit(rule.check(candle(null, "106", "94", "100"), TEN, THOUSAND)))
      .isEqualTo(tuple("95", false, "5.0%손절"));
  }

  @Test
  @DisplayName("평균 단가는 수수료를 넣은 원가 ÷ 보유 수량이다")
  void averageIncludesFee() {
    TakeProfitStopLoss rule = TakeProfitStopLoss.of(null, number("0.1"));

    // 원가 1000 달러 ÷ 99주 = 10.1010101 의 90%
    assertThat(exit(rule.check(candle("9.5", "9.6", "9.05", "9.2"), number("99"), THOUSAND)))
      .isEqualTo(tuple("9.09090909", false, "10.0%손절"));
  }
}
