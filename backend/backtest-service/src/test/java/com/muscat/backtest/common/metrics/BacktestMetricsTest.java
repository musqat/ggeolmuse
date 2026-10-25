package com.muscat.backtest.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("백테스트 지표")
class BacktestMetricsTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final BacktestMetrics metrics = new BacktestMetrics(registry);

  private long count(String kind, String outcome) {
    return registry.get("backtest.run").tags("kind", kind, "outcome", outcome).timer().count();
  }

  @Test
  @DisplayName("끝난 실행은 종류 · 성공으로 소요 시간 한 건을 남기고 결과를 그대로 돌려준다")
  void recordsSuccess() {
    String result = metrics.record("dca", () -> "done");

    assertThat(result).isEqualTo("done");
    assertThat(count("dca", "success")).isEqualTo(1);
  }

  @Test
  @DisplayName("예외로 끝난 실행은 실패로 한 건 남기고 예외를 그대로 던진다")
  void recordsFailure() {
    assertThatThrownBy(() -> metrics.record("simulation", () -> {
      throw new IllegalStateException("시세 없음");
    })).isInstanceOf(IllegalStateException.class);

    assertThat(count("simulation", "failure")).isEqualTo(1);
    assertThat(registry.find("backtest.run").tags("kind", "simulation", "outcome", "success").timer()).isNull();
  }
}
