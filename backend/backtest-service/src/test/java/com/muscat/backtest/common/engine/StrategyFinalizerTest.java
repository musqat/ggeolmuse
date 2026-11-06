package com.muscat.backtest.common.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.common.util.FxRates;
import com.muscat.backtest.domain.model.StrategyTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StrategyFinalizer 날짜별 원화 환율")
class StrategyFinalizerTest {

  private static final LocalDate FIRST = LocalDate.of(2024, 1, 2);
  private static final LocalDate MIDDLE = LocalDate.of(2024, 1, 7);
  private static final LocalDate VALUATION = LocalDate.of(2024, 1, 12);

  private static StrategyTransaction buy(String fxRate) {
    return StrategyTransaction.builder().date(FIRST).actualDate(FIRST).price(new BigDecimal("100"))
      .shares(BigDecimal.TEN).amount(new BigDecimal("1350000")).fxRate(new BigDecimal(fxRate)).trigger("월정액")
      .fee(BigDecimal.ZERO).build();
  }

  @Test
  @DisplayName("시장 환율 기록이 없으면 첫 매수 환율에서 평가일 환율까지 날짜 비례로 잇는다")
  void noRecordsInterpolated() {
    // 첫 매수일은 기본 환율 1350 으로 채운 날
    FxRates dailyFx = new FxRates(Map.of(FIRST, new BigDecimal("1350")), Set.of(FIRST));

    Function<LocalDate, BigDecimal> fx = StrategyFinalizer.fxPath(List.of(buy("1350")), VALUATION, null, null,
      new BigDecimal("1450"), dailyFx);

    assertThat(fx.apply(FIRST)).isEqualByComparingTo("1350");
    assertThat(fx.apply(MIDDLE)).isEqualByComparingTo("1400");
    assertThat(fx.apply(VALUATION)).isEqualByComparingTo("1450");
  }

  @Test
  @DisplayName("평가일 수동 환율만 주면 첫 매수 환율에서 그 값까지 날짜 비례로 잇는다")
  void manualCurrentInterpolated() {
    FxRates dailyFx = new FxRates(Map.of(FIRST, new BigDecimal("1300"), MIDDLE, new BigDecimal("1300")), Set.of());

    Function<LocalDate, BigDecimal> fx = StrategyFinalizer.fxPath(List.of(buy("1300")), VALUATION, null,
      new BigDecimal("1500"), new BigDecimal("1500"), dailyFx);

    assertThat(fx.apply(MIDDLE)).isEqualByComparingTo("1400");
  }

  @Test
  @DisplayName("시장 환율 기록이 있으면 그날 이하 가장 늦은 기록을 쓴다")
  void marketRecords() {
    FxRates dailyFx = new FxRates(Map.of(FIRST, new BigDecimal("1300"), MIDDLE, new BigDecimal("1320")), Set.of());

    Function<LocalDate, BigDecimal> fx = StrategyFinalizer.fxPath(List.of(buy("1300")), VALUATION, null, null,
      new BigDecimal("1340"), dailyFx);

    assertThat(fx.apply(MIDDLE.plusDays(1))).isEqualByComparingTo("1320");
  }
}
