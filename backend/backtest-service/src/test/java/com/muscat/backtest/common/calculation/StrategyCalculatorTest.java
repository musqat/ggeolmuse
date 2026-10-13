package com.muscat.backtest.common.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.commonlib.dto.StockPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 130만원을 환율 1300 에 넣어 10주, 지금 100달러 · 환율 1300 이면 평가액 130만원
@DisplayName("StrategyCalculator 배당 반영")
class StrategyCalculatorTest {

  private static final BigDecimal INVESTED = new BigDecimal("1300000");
  private static final BigDecimal FX_SUM = INVESTED.multiply(new BigDecimal("1300"));
  private static final List<StrategyTransaction> TXS = List.of(StrategyTransaction.builder()
    .date(LocalDate.of(2024, 1, 15)).actualDate(LocalDate.of(2024, 1, 15)).shares(BigDecimal.TEN)
    .amount(INVESTED).fxRate(new BigDecimal("1300")).build());
  private static final StockPriceDto PRICE = new StockPriceDto("AAPL", null, new BigDecimal("100"), null, null, null,
    null, null, null, null, null, null, null, null, "USD", true, null, null);
  private static final FxRateDto FX = new FxRateDto(LocalDate.of(2024, 12, 31), new BigDecimal("1300"));

  private static StrategyCalculationResult calculate(BigDecimal dividends, BigDecimal reinvested, BigDecimal taxRate) {
    return StrategyCalculator.calculate(TXS, INVESTED, BigDecimal.TEN, FX_SUM, PRICE, FX, dividends, reinvested, taxRate);
  }

  @Test
  @DisplayName("재투자하지 않은 배당은 세후 금액을 원화로 바꿔 총자산에 더한다")
  void cashDividendAfterTax() {
    StrategyCalculationResult result = calculate(BigDecimal.TEN, BigDecimal.ZERO, new BigDecimal("0.15"));

    // 10달러 - 세금 1.5 = 8.5달러 × 1300 = 11,050원
    assertThat(result.getTotalAssetKrw()).isEqualByComparingTo("1311050");
    assertThat(result.getTotalReturnKrw()).isEqualByComparingTo("11050");
  }

  @Test
  @DisplayName("세율이 없으면 배당 전액을 더한다")
  void cashDividendWithoutTax() {
    assertThat(calculate(BigDecimal.TEN, null, null).getTotalAssetKrw()).isEqualByComparingTo("1313000");
  }

  @Test
  @DisplayName("재투자했으면 배당은 이미 주식에 들어 있어 현금으로 더하지 않는다")
  void reinvestedDividend() {
    StrategyCalculationResult result = calculate(BigDecimal.TEN, BigDecimal.TEN, new BigDecimal("0.15"));

    assertThat(result.getTotalAssetKrw()).isEqualByComparingTo("1300000");
    assertThat(result.getDividendsReinvested()).isEqualByComparingTo("10");
  }

  @Test
  @DisplayName("배당 값이 없으면 0 으로 채운다")
  void nullDividends() {
    StrategyCalculationResult result = calculate(null, null, null);

    assertThat(result.getTotalDividends()).isEqualByComparingTo("0");
    assertThat(result.getDividendsReinvested()).isEqualByComparingTo("0");
    assertThat(result.getTotalAssetKrw()).isEqualByComparingTo("1300000");
  }
}
