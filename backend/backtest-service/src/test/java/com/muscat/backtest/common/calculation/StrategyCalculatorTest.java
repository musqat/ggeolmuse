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
  @DisplayName("평가일 환율이 기본값이면 그 날을 결과에 남기고, 받은 환율이면 비운다")
  void defaultValuationFxDate() {
    LocalDate valuation = LocalDate.of(2024, 12, 31);
    StrategyCalculationResult defaulted = StrategyCalculator.calculate(TXS, INVESTED, BigDecimal.TEN, FX_SUM, PRICE,
      new FxRateDto(valuation, new BigDecimal("1350"), true), BigDecimal.ZERO, null, null);

    assertThat(defaulted.getDefaultFxValuationDate()).isEqualTo(valuation);
    assertThat(calculate(BigDecimal.ZERO, null, null).getDefaultFxValuationDate()).isNull();
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
  @DisplayName("매도가 없으면 남은 현금 · 실현 손익이 0 이고 평균 단가는 이전 식 그대로다")
  void noSell() {
    StrategyCalculationResult result = calculate(null, null, null);

    assertThat(result.getCashBalance()).isEqualByComparingTo("0");
    assertThat(result.getRealizedProfit()).isEqualByComparingTo("0");
    assertThat(result.getAveragePrice()).isEqualByComparingTo("100");
  }

  @Test
  @DisplayName("전량 매도로 끝나면 총자산은 남은 현금이고 평균 단가가 없다")
  void allSold() {
    StrategyTransaction sold = StrategyTransaction.builder().date(LocalDate.of(2024, 6, 3))
      .actualDate(LocalDate.of(2024, 6, 3)).price(new BigDecimal("110")).shares(new BigDecimal("-10"))
      .amount(BigDecimal.ZERO).fxRate(new BigDecimal("1300")).fee(BigDecimal.ZERO)
      .proceeds(new BigDecimal("1100")).realizedProfit(new BigDecimal("100")).build();

    StrategyCalculationResult result = StrategyCalculator.calculate(List.of(TXS.getFirst(), sold), INVESTED,
      BigDecimal.ZERO, FX_SUM, PRICE, FX, null, null, null);

    // 1100달러 × 1300
    assertThat(result.getTotalAssetKrw()).isEqualByComparingTo("1430000");
    assertThat(result.getTotalReturnKrw()).isEqualByComparingTo("130000");
    assertThat(result.getCurrentValue()).isEqualByComparingTo("0");
    assertThat(result.getCashBalance()).isEqualByComparingTo("1100");
    assertThat(result.getRealizedProfit()).isEqualByComparingTo("100");
    assertThat(result.getAveragePrice()).isNull();
  }

  @Test
  @DisplayName("판 돈으로 다시 사면 평균 단가는 지금 보유분의 원가다")
  void rebuyAverage() {
    StrategyTransaction sold = StrategyTransaction.builder().date(LocalDate.of(2024, 6, 3))
      .actualDate(LocalDate.of(2024, 6, 3)).price(new BigDecimal("110")).shares(new BigDecimal("-10"))
      .amount(BigDecimal.ZERO).fxRate(new BigDecimal("1300")).fee(BigDecimal.ZERO)
      .proceeds(new BigDecimal("1100")).realizedProfit(new BigDecimal("100")).build();
    StrategyTransaction rebuy = StrategyTransaction.builder().date(LocalDate.of(2024, 7, 1))
      .actualDate(LocalDate.of(2024, 7, 1)).price(new BigDecimal("110")).shares(BigDecimal.TEN)
      .amount(BigDecimal.ZERO).fxRate(new BigDecimal("1300")).fee(BigDecimal.ZERO)
      .cashUsed(new BigDecimal("1100")).build();

    StrategyCalculationResult result = StrategyCalculator.calculate(List.of(TXS.getFirst(), sold, rebuy), INVESTED,
      BigDecimal.TEN, FX_SUM, PRICE, FX, null, null, null);

    assertThat(result.getAveragePrice()).isEqualByComparingTo("110");
    assertThat(result.getCashBalance()).isEqualByComparingTo("0");
    assertThat(result.getTotalAssetKrw()).isEqualByComparingTo("1300000");
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
