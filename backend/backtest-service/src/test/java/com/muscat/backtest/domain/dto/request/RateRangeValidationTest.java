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

@DisplayName("수동 환율 · 수수료율 · 배당 세율 범위 검사")
class RateRangeValidationTest {

  private static Validator validator;

  @BeforeAll
  static void init() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  private static BigDecimal decimal(String value) {
    return value == null ? null : new BigDecimal(value);
  }

  // 수동 환율 · 수수료율 · 배당 세율을 받는 요청 일곱(적립식 · 조건부 · 이동평균 교차 · 밴드 · 시뮬레이션 · 종목 비교 · 포트폴리오)
  private static List<Object> requests(String fxRate, String currentFxRate, String feeRate, String taxRate) {
    DcaStrategyRequest dca = new DcaStrategyRequest();
    dca.setPurchaseFxRate(decimal(fxRate));
    dca.setCurrentFxRate(decimal(currentFxRate));
    dca.setTradingFeeRate(decimal(feeRate));
    dca.setDividendTaxRate(decimal(taxRate));
    ConditionalStrategyRequest conditional = new ConditionalStrategyRequest();
    conditional.setPurchaseFxRate(decimal(fxRate));
    conditional.setCurrentFxRate(decimal(currentFxRate));
    conditional.setTradingFeeRate(decimal(feeRate));
    conditional.setDividendTaxRate(decimal(taxRate));
    MaCrossStrategyRequest maCross = new MaCrossStrategyRequest();
    maCross.setPurchaseFxRate(decimal(fxRate));
    maCross.setCurrentFxRate(decimal(currentFxRate));
    maCross.setTradingFeeRate(decimal(feeRate));
    maCross.setDividendTaxRate(decimal(taxRate));
    MaBandStrategyRequest maBand = new MaBandStrategyRequest();
    maBand.setPurchaseFxRate(decimal(fxRate));
    maBand.setCurrentFxRate(decimal(currentFxRate));
    maBand.setTradingFeeRate(decimal(feeRate));
    maBand.setDividendTaxRate(decimal(taxRate));
    SimulationRequest simulation = new SimulationRequest();
    simulation.setPurchaseFxRate(decimal(fxRate));
    simulation.setCurrentFxRate(decimal(currentFxRate));
    simulation.setTradingFeeRate(decimal(feeRate));
    simulation.setDividendTaxRate(decimal(taxRate));
    SymbolComparisonRequest comparison = new SymbolComparisonRequest();
    comparison.setPurchaseFxRate(decimal(fxRate));
    comparison.setCurrentFxRate(decimal(currentFxRate));
    comparison.setTradingFeeRate(decimal(feeRate));
    comparison.setDividendTaxRate(decimal(taxRate));
    PortfolioRequest portfolio = new PortfolioRequest();
    portfolio.setPurchaseFxRate(decimal(fxRate));
    portfolio.setCurrentFxRate(decimal(currentFxRate));
    portfolio.setTradingFeeRate(decimal(feeRate));
    portfolio.setDividendTaxRate(decimal(taxRate));
    return List.of(dca, conditional, maCross, maBand, simulation, comparison, portfolio);
  }

  private static Set<String> violatedFields(Object request) {
    return validator.validate(request).stream().map(v -> v.getPropertyPath().toString()).collect(toSet());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1300"})
  @DisplayName("수동 매수 · 현재 환율이 0 이하면 일곱 요청 모두 막는다")
  void rejectsNonPositiveFxRate(String fxRate) {
    for (Object request : requests(fxRate, fxRate, "0", null)) {
      assertThat(violatedFields(request)).as(request.getClass().getSimpleName())
        .contains("purchaseFxRate", "currentFxRate");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "1.5", "-0.001"})
  @DisplayName("수수료율이 0 미만이거나 1 이상이면 일곱 요청 모두 막는다")
  void rejectsFeeRateOutOfRange(String feeRate) {
    for (Object request : requests(null, null, feeRate, null)) {
      assertThat(violatedFields(request)).as(request.getClass().getSimpleName()).contains("tradingFeeRate");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "1.5", "-0.01"})
  @DisplayName("배당 세율이 0 미만이거나 1 이상이면 일곱 요청 모두 막음")
  void rejectsDividendTaxRateOutOfRange(String taxRate) {
    for (Object request : requests(null, null, null, taxRate)) {
      assertThat(violatedFields(request)).as(request.getClass().getSimpleName()).contains("dividendTaxRate");
    }
  }

  @Test
  @DisplayName("수동 환율이 없거나 양수이고 수수료율 · 배당 세율이 없거나 0 이상 1 미만이면 네 칸은 통과")
  void acceptsInRange() {
    for (List<Object> requests : List.of(requests(null, null, null, null), requests(null, null, "0", "0"),
      requests("1300", "1350", "0.0025", "0.15"))) {
      for (Object request : requests) {
        assertThat(violatedFields(request)).as(request.getClass().getSimpleName())
          .doesNotContain("purchaseFxRate", "currentFxRate", "tradingFeeRate", "dividendTaxRate");
      }
    }
  }
}
