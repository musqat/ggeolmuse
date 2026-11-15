package com.muscat.backtest.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.domain.dto.response.DailyValue;
import com.muscat.backtest.domain.model.HoldingStep;
import com.muscat.backtest.domain.model.SimulationCalculationResult;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SimulationPerformance 단일 매수 일별 가치 · 지표")
class SimulationPerformanceTest {

  private static final LocalDate D1 = LocalDate.of(2024, 1, 2);
  private static final LocalDate D2 = LocalDate.of(2024, 1, 3);
  private static final LocalDate D3 = LocalDate.of(2024, 1, 4);
  private static final LocalDate VALUATION = LocalDate.of(2024, 1, 5);
  private static final Function<LocalDate, BigDecimal> FX = date -> new BigDecimal("1000");

  private static OHLCPriceDto candle(LocalDate date, String close, boolean usable) {
    BigDecimal c = new BigDecimal(close);
    return new OHLCPriceDto("AAPL", date, c, c, c, c, c, 1L, "USD", usable);
  }

  // 원금 100만원 · 매수 환율 1000 · 평가일 환율 1000 · 총자산 140만원과 보유 단계만 채운 결과
  private static SimulationCalculationResult calculation(List<HoldingStep> steps) {
    BigDecimal fx = new BigDecimal("1000");
    return new SimulationCalculationResult(new BigDecimal("1000000"), null, null, null, null, null, null, null, fx, fx,
      null, null, null, null, null, null, new BigDecimal("1400000"), null, null, null, null, steps, null, null);
  }

  @Test
  @DisplayName("일봉마다 당일까지 마지막 보유 단계의 주식 수 × 종가 + 현금, 쓸 수 없는 일봉 · 평가일 일봉은 빼고 평가일은 총자산")
  void valuesFollowSteps() {
    List<OHLCPriceDto> candles = List.of(candle(D1, "100", true), candle(D2, "110", true), candle(D3, "120", false),
      candle(VALUATION, "130", true));
    List<HoldingStep> steps = List.of(new HoldingStep(D1, new BigDecimal("10"), BigDecimal.ZERO),
      new HoldingStep(D2, new BigDecimal("12"), new BigDecimal("5")));

    StrategyPerformance.Result result = SimulationPerformance.of(candles, FX, calculation(steps), VALUATION, D1);

    assertThat(result.dailyValues()).extracting(DailyValue::date).containsExactly(D1, D2, VALUATION);
    assertThat(result.dailyValues()).extracting(DailyValue::valueUsd)
      .usingElementComparator(BigDecimal::compareTo)
      .containsExactly(new BigDecimal("1000"), new BigDecimal("1325"), new BigDecimal("1400"));
    assertThat(result.dailyValues()).extracting(DailyValue::valueKrw)
      .usingElementComparator(BigDecimal::compareTo)
      .containsExactly(new BigDecimal("1000000"), new BigDecimal("1325000"), new BigDecimal("1400000"));
    assertThat(result.metricsKrw()).isNotNull();
    assertThat(result.metricsUsd()).isNotNull();
  }
}
