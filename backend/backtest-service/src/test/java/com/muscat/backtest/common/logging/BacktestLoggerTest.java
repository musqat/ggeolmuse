package com.muscat.backtest.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

@DisplayName("BacktestLogger 로그 컨텍스트")
class BacktestLoggerTest {

  @AfterEach
  void clear() {
    BacktestLogger.clear();
  }

  @Test
  @DisplayName("사용자 · 작업 · 종목을 로그 컨텍스트에 싣는다")
  void setsContext() {
    BacktestLogger.setBacktestContext("user-1", "SIMULATION", "AAPL");

    assertThat(MDC.get(BacktestLogger.USER_ID)).isEqualTo("user-1");
    assertThat(MDC.get(BacktestLogger.OPERATION)).isEqualTo("SIMULATION");
    assertThat(MDC.get("symbol")).isEqualTo("AAPL");
  }

  @Test
  @DisplayName("비어 있는 사용자 · 작업 · 종목은 싣지 않는다")
  void skipsBlank() {
    BacktestLogger.setBacktestContext("  ", " ", null);
    BacktestLogger.setStrategyContext(null, "DCA", null);

    assertThat(MDC.get(BacktestLogger.USER_ID)).isNull();
    assertThat(MDC.get("symbol")).isNull();
    assertThat(MDC.get(BacktestLogger.OPERATION)).isEqualTo("STRATEGY_DCA");
  }

  @Test
  @DisplayName("작업만 지우면 사용자는 남는다")
  void removesOnlyKey() {
    BacktestLogger.setStrategyContext("user-1", "DCA", "AAPL");

    BacktestLogger.remove(BacktestLogger.OPERATION);

    assertThat(MDC.get(BacktestLogger.OPERATION)).isNull();
    assertThat(MDC.get(BacktestLogger.USER_ID)).isEqualTo("user-1");
  }
}
