package com.muscat.backtest.common.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.backtest.common.util.FxRates;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
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
      BigDecimal.ZERO, new ArrayList<>(), Map.of(), FxRates.EMPTY);
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
}
