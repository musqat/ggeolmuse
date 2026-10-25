package com.muscat.backtest.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 백테스트 한 번의 소요 시간 · 완료 수 지표(backtest.run, 태그 종류 · 결과)
@Component
@RequiredArgsConstructor
public class BacktestMetrics {

  private static final String NAME = "backtest.run";

  private final MeterRegistry registry;

  public <T> T record(String kind, Supplier<T> run) {
    Timer.Sample sample = Timer.start(registry);
    String outcome = "failure";
    try {
      T result = run.get();
      outcome = "success";
      return result;
    } finally {
      sample.stop(Timer.builder(NAME)
        .description("백테스트 한 번의 소요 시간")
        .tag("kind", kind)
        .tag("outcome", outcome)
        .register(registry));
    }
  }
}
