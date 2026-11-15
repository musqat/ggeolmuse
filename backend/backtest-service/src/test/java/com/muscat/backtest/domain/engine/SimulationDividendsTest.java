package com.muscat.backtest.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.model.SimulationContext;
import com.muscat.backtest.domain.dto.market.DividendDto;
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

@DisplayName("SimulationDividends 단일 매수 배당")
class SimulationDividendsTest {

  private static final LocalDate BUY = LocalDate.of(2024, 1, 2);
  private static final LocalDate EX1 = LocalDate.of(2024, 3, 1);
  private static final LocalDate EX2 = LocalDate.of(2024, 6, 3);
  private static final LocalDate VALUATION = LocalDate.of(2024, 12, 31);
  private static final Function<LocalDate, BigDecimal> FX = date -> new BigDecimal("1000");

  private static DividendDto dividend(LocalDate exDate, String amount) {
    return new DividendDto("AAPL", exDate, exDate, null, amount == null ? null : new BigDecimal(amount), "USD", "test");
  }

  // 세율 0.15 로 BUY 에 산 단일 매수(배당은 받은 목록)
  private static SimulationContext context(List<DividendDto> dividends) {
    SimulationRequest request = SimulationRequest.builder().symbol("AAPL").purchaseDate(BUY)
      .dividendTaxRate(new BigDecimal("0.15")).build();
    BigDecimal price = new BigDecimal("100");
    OHLCPriceDto bought = new OHLCPriceDto("AAPL", BUY, price, price, price, price, price, 1L, "USD", true);
    return new SimulationContext(request, bought, new FxRateDto(BUY, new BigDecimal("1000")), price,
      new FxRateDto(VALUATION, new BigDecimal("1000")), DividendHistoryDto.of("AAPL", dividends), VALUATION, Map.of());
  }

  private static HoldingTrail trail() {
    return new HoldingTrail(null, Map.of(), null, FX);
  }

  @Test
  @DisplayName("재투자는 세금을 두 자리 반올림해 뺀 세후 배당으로 배당락일 종가에 더 사고 세전 합 · 재투자 합 · 날짜를 남김")
  void reinvestAfterTax() {
    HoldingTrail trail = trail();

    SimulationDividends.Result result = SimulationDividends.reinvest(context(List.of(dividend(EX1, "0.24"))), trail,
      new BigDecimal("10"), new BigDecimal("5"), date -> new BigDecimal("50"));

    assertThat(result.shares()).isEqualByComparingTo("10.0408");
    assertThat(result.cash()).isEqualByComparingTo("5");
    assertThat(result.gross()).isEqualByComparingTo("2.40");
    assertThat(result.reinvested()).isEqualByComparingTo("2.04");
    assertThat(result.reinvestDates()).containsExactly(EX1);
    assertThat(trail.taxTransactions()).singleElement()
      .satisfies(tx -> assertThat(tx.getCashUsed()).isEqualByComparingTo("2.04"));
  }

  @Test
  @DisplayName("매수일 · 평가일 밖의 배당과 금액 0 배당은 받지 않고 배당락일 종가도 찾지 않음")
  void skipsOutsideAndZero() {
    List<DividendDto> dividends = List.of(dividend(BUY, "1"), dividend(EX1, "0"),
      dividend(VALUATION.plusDays(1), "1"), dividend(null, "1"));

    SimulationDividends.Result result = SimulationDividends.reinvest(context(dividends), trail(),
      new BigDecimal("10"), BigDecimal.ZERO, date -> {
        throw new AssertionError("종가를 찾음: " + date);
      });

    assertThat(result.shares()).isEqualByComparingTo("10");
    assertThat(result.gross()).isEqualByComparingTo("0");
    assertThat(result.reinvestDates()).isEmpty();
  }

  @Test
  @DisplayName("재투자를 끄면 세후 배당을 반올림 없이 현금에 쌓고 배당락일마다 보유 단계를 남김")
  void keepAsCash() {
    HoldingTrail trail = trail();

    SimulationDividends.Result result = SimulationDividends.keepAsCash(
      context(List.of(dividend(EX1, "0.333"), dividend(EX2, "0.10"), dividend(EX2.plusDays(7), null))), trail,
      new BigDecimal("10"), new BigDecimal("5"));

    assertThat(result.shares()).isEqualByComparingTo("10");
    assertThat(result.cash()).isEqualByComparingTo("8.6805");
    assertThat(result.gross()).isEqualByComparingTo("4.33");
    assertThat(result.reinvested()).isEqualByComparingTo("0");
    assertThat(trail.steps()).extracting(step -> step.from()).containsExactly(EX1, EX2);
  }
}
