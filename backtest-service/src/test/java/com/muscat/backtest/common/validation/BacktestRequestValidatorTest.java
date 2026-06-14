package com.muscat.backtest.common.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.exception.BacktestException;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BacktestRequestValidator 단위 테스트")
class BacktestRequestValidatorTest {

  @Test
  @DisplayName("null 요청 → STRATEGY_REQUEST_NULL")
  void requireNonNull() {
    assertThatThrownBy(() -> BacktestRequestValidator.requireNonNull(null))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode").isEqualTo(BacktestResponse.STRATEGY_REQUEST_NULL);
  }

  @Test
  @DisplayName("빈 심볼 → STRATEGY_SYMBOL_REQUIRED")
  void requireSymbol_blank() {
    assertThatThrownBy(() -> BacktestRequestValidator.requireSymbol("  "))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode").isEqualTo(BacktestResponse.STRATEGY_SYMBOL_REQUIRED);
    assertThatThrownBy(() -> BacktestRequestValidator.requireSymbol(null))
        .isInstanceOf(BacktestException.class);
  }

  @Test
  @DisplayName("시작일 null / 종료일 null / 역전 각각 해당 에러")
  void requireDateRange() {
    LocalDate s = LocalDate.of(2025, 1, 1);
    LocalDate e = LocalDate.of(2026, 1, 1);
    assertThatThrownBy(() -> BacktestRequestValidator.requireDateRange(null, e))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode").isEqualTo(BacktestResponse.STRATEGY_START_DATE_REQUIRED);
    assertThatThrownBy(() -> BacktestRequestValidator.requireDateRange(s, null))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode").isEqualTo(BacktestResponse.STRATEGY_END_DATE_REQUIRED);
    assertThatThrownBy(() -> BacktestRequestValidator.requireDateRange(e, s))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode").isEqualTo(BacktestResponse.STRATEGY_DATE_RANGE_INVALID);
  }

  @Test
  @DisplayName("유효 입력은 통과")
  void valid() {
    assertThatCode(() -> {
      BacktestRequestValidator.requireNonNull("x");
      BacktestRequestValidator.requireSymbol("AAPL");
      BacktestRequestValidator.requireDateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1));
    }).doesNotThrowAnyException();
  }
}
