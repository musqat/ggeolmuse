package com.muscat.backtest.common.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.exception.BacktestException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("BacktestRequestValidator 단위 테스트")
class BacktestRequestValidatorTest {

  @Test
  @DisplayName("null 요청 → STRATEGY_REQUEST_NULL")
  void requireNonNull() {
    assertThatThrownBy(() -> BacktestRequestValidator.requireNonNull(null))
        .isInstanceOf(BacktestException.class)
        .extracting("code").isEqualTo(BacktestResponse.STRATEGY_REQUEST_NULL);
  }

  @Test
  @DisplayName("빈 심볼 → STRATEGY_SYMBOL_REQUIRED")
  void requireSymbol_blank() {
    assertThatThrownBy(() -> BacktestRequestValidator.requireSymbol("  "))
        .isInstanceOf(BacktestException.class)
        .extracting("code").isEqualTo(BacktestResponse.STRATEGY_SYMBOL_REQUIRED);
    assertThatThrownBy(() -> BacktestRequestValidator.requireSymbol(null))
        .isInstanceOf(BacktestException.class);
  }

  @Test
  @DisplayName("^ 로 시작하는 지수 심볼 → INDEX_NOT_TRADABLE")
  void requireSymbol_index() {
    assertThatThrownBy(() -> BacktestRequestValidator.requireSymbol("^GSPC"))
        .isInstanceOf(BacktestException.class)
        .extracting("code").isEqualTo(BacktestResponse.INDEX_NOT_TRADABLE);
    assertThatThrownBy(() -> BacktestRequestValidator.requireTradable(" ^NDX"))
        .isInstanceOf(BacktestException.class)
        .extracting("code").isEqualTo(BacktestResponse.INDEX_NOT_TRADABLE);
    assertThatCode(() -> BacktestRequestValidator.requireTradable(null)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("시작일 null / 종료일 null / 역전 각각 해당 에러")
  void requireDateRange() {
    LocalDate s = LocalDate.of(2025, 1, 1);
    LocalDate e = LocalDate.of(2026, 1, 1);
    assertThatThrownBy(() -> BacktestRequestValidator.requireDateRange(null, e))
        .isInstanceOf(BacktestException.class)
        .extracting("code").isEqualTo(BacktestResponse.STRATEGY_START_DATE_REQUIRED);
    assertThatThrownBy(() -> BacktestRequestValidator.requireDateRange(s, null))
        .isInstanceOf(BacktestException.class)
        .extracting("code").isEqualTo(BacktestResponse.STRATEGY_END_DATE_REQUIRED);
    assertThatThrownBy(() -> BacktestRequestValidator.requireDateRange(e, s))
        .isInstanceOf(BacktestException.class)
        .extracting("code").isEqualTo(BacktestResponse.STRATEGY_DATE_RANGE_INVALID);
  }

  private static BigDecimal rate(String value) {
    return value == null ? null : new BigDecimal(value);
  }

  @ParameterizedTest(name = "하락 {0} · 팔 {1}")
  @CsvSource({"0,", "1,", "-0.1,", "0.1,0", "0.1,1.01", "0.1,-0.5", ",0.5"})
  @DisplayName("트레일링 스탑 하락 비율이 0 초과 1 미만 밖이거나 팔 비율이 0 초과 1 이하 밖이거나 팔 비율만 있으면 INVALID_TRAILING_STOP")
  void requireTrailingStop_rejected(String stopRate, String sellRate) {
    assertThatThrownBy(() -> BacktestRequestValidator.requireTrailingStop(rate(stopRate), rate(sellRate)))
        .isInstanceOf(BacktestException.class)
        .extracting("code").isEqualTo(BacktestResponse.INVALID_TRAILING_STOP);
  }

  @ParameterizedTest(name = "하락 {0} · 팔 {1}")
  @CsvSource({",", "0.1,", "0.99,1", "0.01,0.01"})
  @DisplayName("트레일링 스탑은 둘 다 비우거나 하락 비율이 0 초과 1 미만이고 팔 비율을 비우거나 0 초과 1 이하면 통과한다")
  void requireTrailingStop_accepted(String stopRate, String sellRate) {
    assertThatCode(() -> BacktestRequestValidator.requireTrailingStop(rate(stopRate), rate(sellRate)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("유효 입력은 통과")
  void valid() {
    assertThatCode(() -> {
      BacktestRequestValidator.requireNonNull("x");
      BacktestRequestValidator.requireSymbol("AAPL");
      BacktestRequestValidator.requireTradable("BRK.B");
      BacktestRequestValidator.requireDateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1));
    }).doesNotThrowAnyException();
  }
}
