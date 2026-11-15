package com.muscat.backtest.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.model.SimulationCalculationResult;
import com.muscat.backtest.domain.model.SimulationContext;
import com.muscat.backtest.domain.dto.market.DividendHistoryDto;
import com.muscat.backtest.domain.dto.market.FxRateDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SimulationCalculator 단일 매수 손익")
class SimulationCalculatorTest {

  private static final LocalDate BUY = LocalDate.of(2024, 1, 2);
  private static final LocalDate VALUATION = LocalDate.of(2024, 12, 31);
  private static final Function<LocalDate, BigDecimal> FX = date -> new BigDecimal("1300");
  private static final Function<LocalDate, BigDecimal> NO_CLOSE = date -> {
    throw new AssertionError("종가를 찾음: " + date);
  };

  // 100달러 · 환율 1300 에 사서 평가일 120달러 · 환율 1400, 수수료율 0.001, 배당 없음
  private static SimulationContext context(SimulationRequest.SimulationRequestBuilder<?, ?> request) {
    BigDecimal price = new BigDecimal("100");
    OHLCPriceDto bought = new OHLCPriceDto("AAPL", BUY, price, price, price, price, price, 1L, "USD", true);
    return new SimulationContext(request.symbol("AAPL").purchaseDate(BUY).tradingFeeRate(new BigDecimal("0.001")).build(),
      bought, new FxRateDto(BUY, new BigDecimal("1300")), new BigDecimal("120"),
      new FxRateDto(VALUATION, new BigDecimal("1400")), DividendHistoryDto.of("AAPL", List.of()), VALUATION,
      Map.of(BUY, bought));
  }

  @Test
  @DisplayName("투자 금액이면 산 날 환율로 바꾼 달러에서 수수료를 빼고 사고 평가일 주가 · 환율로 총자산 · 수익을 냄")
  void amountPurchase() {
    SimulationCalculationResult result = SimulationCalculator.calculate(
      context(SimulationRequest.builder().investmentAmount(new BigDecimal("1300000"))), FX, NO_CLOSE);

    assertThat(result.investmentKrw()).isEqualByComparingTo("1300000");
    assertThat(result.shares()).isEqualByComparingTo("9.99");
    assertThat(result.tradingFee()).isEqualByComparingTo("1.00");
    assertThat(result.remainingCash()).isEqualByComparingTo("0");
    assertThat(result.stockReturn()).isEqualByComparingTo("20");
    assertThat(result.fxReturn()).isEqualByComparingTo("100");
    assertThat(result.totalAssetKrw()).isEqualByComparingTo("1678320");
    assertThat(result.totalReturnKrw()).isEqualByComparingTo("378320");
    assertThat(result.holdingSteps()).singleElement().satisfies(step -> {
      assertThat(step.from()).isEqualTo(BUY);
      assertThat(step.shares()).isEqualByComparingTo("9.99");
    });
    assertThat(result.transactions()).isNull();
  }

  @Test
  @DisplayName("주식 수면 원금은 주식 수 × 종가 + 수수료를 산 날 환율로 바꾼 값이고 남은 현금은 0")
  void sharesPurchase() {
    SimulationCalculationResult result = SimulationCalculator.calculate(
      context(SimulationRequest.builder().sharesPerPurchase(3)), FX, NO_CLOSE);

    assertThat(result.investmentKrw()).isEqualByComparingTo("390390");
    assertThat(result.shares()).isEqualByComparingTo("3");
    assertThat(result.tradingFee()).isEqualByComparingTo("0.30");
    assertThat(result.remainingCash()).isEqualByComparingTo("0");
    assertThat(result.taxTransactions()).singleElement()
      .satisfies(tx -> assertThat(tx.getCashUsed()).isEqualByComparingTo("300.30"));
  }
}
