package com.muscat.backtest.common.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.util.FxRates;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.response.DailyValue;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StrategyFinalizer 날짜별 원화 환율 · 매수 0회 마무리")
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
  @DisplayName("시장 환율 기록이 있으면 그 날짜 이하 가장 늦은 기록을 쓴다")
  void marketRecords() {
    FxRates dailyFx = new FxRates(Map.of(FIRST, new BigDecimal("1300"), MIDDLE, new BigDecimal("1320")), Set.of());

    Function<LocalDate, BigDecimal> fx = StrategyFinalizer.fxPath(List.of(buy("1300")), VALUATION, null, null,
      new BigDecimal("1340"), dailyFx);

    assertThat(fx.apply(MIDDLE.plusDays(1))).isEqualByComparingTo("1320");
  }

  @Test
  @DisplayName("매수가 없으면 배당을 조회하지 않고 투자 · 평가 · 수익 0 과 빈 일별 가치로 끝내 응답에 옮긴다")
  void noTransactionsZeroResult() {
    MarketDataClientWrapper marketData = mock(MarketDataClientWrapper.class);
    BigDecimal close = new BigDecimal("120");
    given(marketData.getOHLCPrice(eq("AAPL"), anyString()))
      .willReturn(new OHLCPriceDto("AAPL", VALUATION, close, close, close, close, close, 1_000L, "USD", true));
    given(marketData.getFxRate(anyString())).willReturn(new FxRateDto(VALUATION, new BigDecimal("1350")));

    StrategyFinalizer.Result result = StrategyFinalizer.run(marketData, "AAPL", FIRST, VALUATION, null, null, false,
      BigDecimal.ZERO, new ArrayList<>(), Map.of(), FxRates.EMPTY, PeriodEnd.SELL_AT_END, BigDecimal.ZERO);
    ConditionalStrategyRequest request = ConditionalStrategyRequest.builder().symbol("AAPL").startDate(FIRST)
      .endDate(VALUATION).build();
    StrategyResponse response = result.fill(new ResponseMapper().toStrategyResponse(request, result.transactions(),
      result.calculation(), result.currentPrice(), List.of()));

    assertThat(response.getTotalTransactions()).isZero();
    assertThat(response.getTotalInvested()).isEqualByComparingTo("0");
    assertThat(response.getTotalShares()).isEqualByComparingTo("0");
    assertThat(response.getCurrentValueKrw()).isEqualByComparingTo("0");
    assertThat(response.getTotalReturnPercent()).isEqualByComparingTo("0");
    assertThat(response.getDividendYield()).isEqualByComparingTo("0");
    assertThat(response.getAveragePrice()).isNull();
    assertThat(response.getAverageFxRate()).isNull();
    assertThat(response.getCurrentPrice()).isEqualByComparingTo("120");
    assertThat(response.getCurrentFxRate()).isEqualByComparingTo("1350");
    assertThat(response.getDailyValues()).isEmpty();
    assertThat(response.getMetricsKrw()).isNull();
    assertThat(response.getMetricsUsd()).isNull();
    verify(marketData, never()).getDividendHistory(anyString(), anyString(), anyString());
  }

  @Nested
  @DisplayName("기간 끝 매도")
  class PeriodEndSale {

    private final MarketDataClientWrapper marketData = mock(MarketDataClientWrapper.class);

    // 평가일 시세 120 · 평가일 환율 1350 · 배당 없음
    private StrategyFinalizer.Result run(List<StrategyTransaction> transactions, Map<LocalDate, OHLCPriceDto> priceMap,
      PeriodEnd periodEnd) {
      given(marketData.getOHLCPrice(eq("AAPL"), anyString())).willReturn(candle(VALUATION, "120"));
      given(marketData.getFxRate(anyString())).willReturn(new FxRateDto(VALUATION, new BigDecimal("1350")));
      given(marketData.getDividendHistory(eq("AAPL"), anyString(), anyString())).willReturn(List.of());
      return StrategyFinalizer.run(marketData, "AAPL", FIRST, VALUATION, null, null, false, BigDecimal.ZERO,
        new ArrayList<>(transactions), priceMap, FxRates.EMPTY, periodEnd, new BigDecimal("0.01"));
    }

    private OHLCPriceDto candle(LocalDate date, String close) {
      BigDecimal price = new BigDecimal(close);
      return new OHLCPriceDto("AAPL", date, price, price, price, price, price, 1_000L, "USD", true);
    }

    @Test
    @DisplayName("기간 끝 매도를 고르면 남은 주식 전량을 마지막 거래일 종가에 수수료를 빼고 팔아 평가일 환율을 남기고 총 자산은 판 돈이다")
    void sellsAtLastClose() {
      StrategyFinalizer.Result result = run(List.of(buy("1350")),
        Map.of(FIRST, candle(FIRST, "100"), MIDDLE, candle(MIDDLE, "110")), PeriodEnd.SELL_AT_END);

      StrategyTransaction sale = result.transactions().getLast();
      assertThat(sale.getTrigger()).isEqualTo("기간 끝 매도");
      assertThat(sale.tradedOn()).isEqualTo(MIDDLE);
      assertThat(sale.getPrice()).isEqualByComparingTo("110");
      assertThat(sale.getShares()).isEqualByComparingTo("-10");
      assertThat(sale.getAmount()).isEqualByComparingTo("0");
      // 1100달러 - 수수료 11달러, 원가 135만원 ÷ 1350원 = 1000달러
      assertThat(sale.getFee()).isEqualByComparingTo("11.00");
      assertThat(sale.getProceeds()).isEqualByComparingTo("1089");
      assertThat(sale.getRealizedProfit()).isEqualByComparingTo("89");
      assertThat(sale.getFxRate()).isEqualByComparingTo("1350");
      assertThat(result.calculation().getTotalShares()).isEqualByComparingTo("0");
      assertThat(result.calculation().getCurrentValueKrw()).isEqualByComparingTo("0");
      assertThat(result.calculation().getCashBalance()).isEqualByComparingTo("1089");
      assertThat(result.calculation().getTotalAssetKrw()).isEqualByComparingTo("1470150");
      // 판 날부터 일별 가치는 판 돈
      assertThat(result.performance().dailyValues()).extracting(DailyValue::valueUsd)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("1000.00"), new BigDecimal("1089.00"), new BigDecimal("1089.00"));
      assertThat(result.capitalGainsTax().years()).singleElement()
        .satisfies(year -> assertThat(year.realizedGainKrw()).isEqualByComparingTo("120150"));
      // 판 날 뒤 배당은 받지 않음
      verify(marketData).getDividendHistory("AAPL", "2024-01-02", "2024-01-07");
    }

    @Test
    @DisplayName("기간 일봉이 없으면 평가일 시세에 평가일로 판다")
    void sellsAtValuationPriceWithoutCandles() {
      StrategyTransaction sale = run(List.of(buy("1350")), Map.of(), PeriodEnd.SELL_AT_END).transactions().getLast();

      assertThat(sale.tradedOn()).isEqualTo(VALUATION);
      assertThat(sale.getPrice()).isEqualByComparingTo("120");
    }

    @Test
    @DisplayName("끝까지 보유하거나 이미 전량 팔았으면 기간 끝 매도를 더하지 않는다")
    void noSaleWhenHoldingOrSoldOut() {
      StrategyTransaction soldOut = StrategyTransaction.builder().date(MIDDLE).actualDate(MIDDLE).price(new BigDecimal("110"))
        .shares(new BigDecimal("-10")).amount(BigDecimal.ZERO).proceeds(new BigDecimal("1100")).fee(BigDecimal.ZERO)
        .realizedProfit(new BigDecimal("100")).fxRate(new BigDecimal("1350")).trigger("10.0%익절").build();
      Map<LocalDate, OHLCPriceDto> candles = Map.of(FIRST, candle(FIRST, "100"), MIDDLE, candle(MIDDLE, "110"));

      assertThat(run(List.of(buy("1350")), candles, PeriodEnd.HOLD).transactions()).hasSize(1);
      assertThat(run(List.of(buy("1350")), candles, null).transactions()).hasSize(1);
      assertThat(run(List.of(buy("1350"), soldOut), candles, PeriodEnd.SELL_AT_END).transactions()).hasSize(2);
    }
  }
}
