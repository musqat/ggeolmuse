package com.muscat.backtest.domain.dto.request;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("수동 환율 · 수수료율 범위 검사")
class RateRangeValidationTest {

  private static Validator validator;

  @BeforeAll
  static void init() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  private static BigDecimal decimal(String value) {
    return value == null ? null : new BigDecimal(value);
  }

  // 수동 환율 · 수수료율을 받는 요청 넷(적립식 · 조건부 · 시뮬레이션 · 종목 비교)
  private static List<Object> requests(String fxRate, String currentFxRate, String feeRate) {
    DcaStrategyRequest dca = new DcaStrategyRequest();
    dca.setPurchaseFxRate(decimal(fxRate));
    dca.setCurrentFxRate(decimal(currentFxRate));
    dca.setTradingFeeRate(decimal(feeRate));
    ConditionalStrategyRequest conditional = new ConditionalStrategyRequest();
    conditional.setPurchaseFxRate(decimal(fxRate));
    conditional.setCurrentFxRate(decimal(currentFxRate));
    conditional.setTradingFeeRate(decimal(feeRate));
    SimulationRequest simulation = new SimulationRequest();
    simulation.setPurchaseFxRate(decimal(fxRate));
    simulation.setCurrentFxRate(decimal(currentFxRate));
    simulation.setTradingFeeRate(decimal(feeRate));
    SymbolComparisonRequest comparison = new SymbolComparisonRequest();
    comparison.setPurchaseFxRate(decimal(fxRate));
    comparison.setCurrentFxRate(decimal(currentFxRate));
    comparison.setTradingFeeRate(decimal(feeRate));
    return List.of(dca, conditional, simulation, comparison);
  }

  private static Set<String> violatedFields(Object request) {
    return validator.validate(request).stream().map(v -> v.getPropertyPath().toString()).collect(toSet());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1300"})
  @DisplayName("수동 매수 · 현재 환율이 0 이하면 네 요청 모두 막는다")
  void rejectsNonPositiveFxRate(String fxRate) {
    for (Object request : requests(fxRate, fxRate, "0")) {
      assertThat(violatedFields(request)).as(request.getClass().getSimpleName())
        .contains("purchaseFxRate", "currentFxRate");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "1.5", "-0.001"})
  @DisplayName("수수료율이 0 미만이거나 1 이상이면 네 요청 모두 막는다")
  void rejectsFeeRateOutOfRange(String feeRate) {
    for (Object request : requests(null, null, feeRate)) {
      assertThat(violatedFields(request)).as(request.getClass().getSimpleName()).contains("tradingFeeRate");
    }
  }

  @Test
  @DisplayName("수동 환율이 없거나 양수이고 수수료율이 없거나 0 이상 1 미만이면 세 칸은 통과한다")
  void acceptsInRange() {
    for (List<Object> requests : List.of(requests(null, null, null), requests(null, null, "0"), requests("1300", "1350", "0.0025"))) {
      for (Object request : requests) {
        assertThat(violatedFields(request)).as(request.getClass().getSimpleName())
          .doesNotContain("purchaseFxRate", "currentFxRate", "tradingFeeRate");
      }
    }
  }
}
