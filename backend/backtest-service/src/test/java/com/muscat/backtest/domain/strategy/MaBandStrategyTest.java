package com.muscat.backtest.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.muscat.backtest.common.calculation.StrategyCalculationResult;
import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.SkipReason;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.domain.dto.request.MaBandStrategyRequest;
import com.muscat.backtest.domain.dto.request.MaCrossStrategyRequest;
import com.muscat.backtest.domain.dto.response.CapitalGainsTax;
import com.muscat.backtest.domain.dto.response.SignalChart;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.dto.response.TradeStats;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.SkippedRound;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MaBandStrategy 테스트")
class MaBandStrategyTest {

  private static final String SYMBOL = "BAND";
  private static final LocalDate START = LocalDate.of(2024, 3, 4);

  @Mock
  private MarketDataClientWrapper marketDataClient;

  @Mock
  private ResponseMapper responseMapper;

  private MaBandStrategy strategy;

  @BeforeEach
  void setUp() {
    strategy = new MaBandStrategy(marketDataClient, responseMapper);
    lenient().when(marketDataClient.defaultFxRate()).thenReturn(new BigDecimal("1300"));
  }

  // 시작일에서 offset 일 뒤 일봉(종가 · 수정 종가 같음)
  private static OHLCPriceDto day(int offset, String close) {
    BigDecimal price = new BigDecimal(close);
    return new OHLCPriceDto(SYMBOL, START.plusDays(offset), price, price, price, price, price, 1_000L, "USD", true);
  }

  // 종가와 수정 종가가 다른 일봉
  private static OHLCPriceDto adjusted(int offset, String close, String adjustedClose) {
    BigDecimal price = new BigDecimal(close);
    return new OHLCPriceDto(SYMBOL, START.plusDays(offset), price, price, price, price, new BigDecimal(adjustedClose),
      1_000L, "USD", true);
  }

  // 3일 평균 ±10%: 1일째 위 선(14.67) 위 20 에 사고, 3일째 20 은 사이(18.60 ~ 22.73), 4일째 아래 선(17.70) 아래 17 에 팔고,
  // 5일째 18 은 사이(16.50 ~ 20.17), 6일째 위 선(22.00) 위 25 에 판 돈으로 다시 삼, 시작일 앞 이틀은 평균용
  private static List<OHLCPriceDto> banded() {
    return List.of(day(-2, "10"), day(-1, "10"), day(0, "10"), day(1, "20"), day(2, "22"), day(3, "20"),
      day(4, "17"), day(5, "18"), day(6, "25"), day(7, "26"));
  }

  // 원금 130만원(환율 1300 고정이면 1000달러) · 3일 평균 · 위 · 아래 10% · 수수료 없음
  private static MaBandStrategyRequest.MaBandStrategyRequestBuilder request(int days) {
    return MaBandStrategyRequest.builder().userId("test-user").symbol(SYMBOL)
      .startDate(START).endDate(START.plusDays(days))
      .investmentAmount(new BigDecimal("1300000"))
      .bandWindow(3).upperRate(new BigDecimal("0.1")).lowerRate(new BigDecimal("0.1"))
      .purchaseFxRate(new BigDecimal("1300"));
  }

  // 기간 시세 · 평가일(과거) 시세와 환율 · 배당
  private void stubMarket(List<OHLCPriceDto> prices) {
    given(marketDataClient.getOHLCPriceRange(eq(SYMBOL), anyString(), anyString())).willReturn(prices);
    lenient().when(marketDataClient.getOHLCPrice(eq(SYMBOL), anyString())).thenReturn(prices.getLast());
    lenient().when(marketDataClient.getFxRate(anyString())).thenReturn(new FxRateDto(START, new BigDecimal("1300")));
    lenient().when(marketDataClient.getDividendHistory(eq(SYMBOL), anyString(), anyString())).thenReturn(List.of());
    given(responseMapper.toStrategyResponse(any(MaBandStrategyRequest.class), any(), any(), any(), any()))
      .willReturn(StrategyResponse.builder().build());
  }

  // 시작일 ~ 시작일 + days 날마다 1300원, 고른 날은 그 값
  private void stubDailyFx(int days, Map<Integer, String> overrides) {
    Map<String, BigDecimal> rates = new HashMap<>();
    for (int offset = 0; offset <= days; offset++) {
      rates.put(START.plusDays(offset).toString(), new BigDecimal(overrides.getOrDefault(offset, "1300")));
    }
    given(marketDataClient.getBulkFxRates(any())).willReturn(rates);
  }

  private List<StrategyTransaction> executed() {
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<StrategyTransaction>> captor = ArgumentCaptor.forClass(List.class);
    verify(responseMapper).toStrategyResponse(any(MaBandStrategyRequest.class), captor.capture(), any(), any(), any());
    return captor.getValue();
  }

  private StrategyCalculationResult calculation() {
    ArgumentCaptor<StrategyCalculationResult> captor = ArgumentCaptor.forClass(StrategyCalculationResult.class);
    verify(responseMapper).toStrategyResponse(any(MaBandStrategyRequest.class), any(), captor.capture(), any(), any());
    return captor.getValue();
  }

  @SuppressWarnings("unchecked")
  private List<SkippedRound> skippedRounds() {
    ArgumentCaptor<List<SkippedRound>> captor = ArgumentCaptor.forClass(List.class);
    verify(responseMapper).toStrategyResponse(any(MaBandStrategyRequest.class), any(), any(), any(), captor.capture());
    return captor.getValue();
  }

  // 거래일 · 가격 · 이유 · 주식 수
  private List<Tuple> trades() {
    return executed().stream()
      .map(tx -> tuple(tx.getDate(), plain(tx.getPrice()), tx.getTrigger(), plain(tx.getShares())))
      .toList();
  }

  private static String plain(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  @Test
  @DisplayName("전략 타입은 MA_BAND")
  void strategyType() {
    assertThat(strategy.getStrategyType()).isEqualTo(StrategyType.MA_BAND);
  }

  @Nested
  @DisplayName("밴드 매매")
  class BandTrades {

    @Test
    @DisplayName("수정 종가가 위 선보다 높은 날 원금 전부로 사고, 사이에서는 그대로 두고, 아래 선보다 낮은 날 전량 팔고, 다시 위 선 위면 판 돈 전부로 산다")
    void buysAboveSellsBelow() {
      stubMarket(banded());

      strategy.execute(request(7).build());

      assertThat(trades()).containsExactly(
        tuple(START.plusDays(1), "20", "밴드 위 매수", "50"),
        tuple(START.plusDays(4), "17", "밴드 아래 매도", "-50"),
        tuple(START.plusDays(6), "25", "밴드 위 매수", "34"));
      List<StrategyTransaction> trades = executed();
      assertThat(trades.get(0).getAmount()).isEqualByComparingTo("1300000");
      assertThat(trades.get(1).getProceeds()).isEqualByComparingTo("850");
      assertThat(trades.get(1).getRealizedProfit()).isEqualByComparingTo("-150");
      assertThat(trades.get(2).getAmount()).isEqualByComparingTo("0");
      assertThat(trades.get(2).getCashUsed()).isEqualByComparingTo("850");
      StrategyCalculationResult calculation = calculation();
      assertThat(calculation.getTotalInvested()).isEqualByComparingTo("1300000");
      assertThat(calculation.getTotalShares()).isEqualByComparingTo("34");
      // 34주 × 26달러 × 1300원
      assertThat(calculation.getTotalAssetKrw()).isEqualByComparingTo("1149200");
    }

    @Test
    @DisplayName("시작일 전부터 위 선 위에 있으면 첫 거래일에 산다")
    void buysOnFirstDayWhenAlreadyAbove() {
      // 시작일 전날 30 은 위 선(22) 위, 시작일 31 도 위 선(29.70) 위
      stubMarket(List.of(day(-3, "10"), day(-2, "20"), day(-1, "30"), day(0, "31"), day(1, "32")));

      strategy.execute(request(1).build());

      assertThat(trades()).containsExactly(tuple(START, "31", "밴드 위 매수", plain(new BigDecimal("32.25806452"))));
    }

    @Test
    @DisplayName("평균 기간 2 · 비율 0 이면 평균보다 높은 날 사고 낮은 날 팔며 평균과 같은 날은 그대로 둔다")
    void zeroRatesTradeAroundAverage() {
      stubMarket(List.of(day(-1, "10"), day(0, "10"), day(1, "12"), day(2, "11")));

      strategy.execute(request(2).bandWindow(2).upperRate(BigDecimal.ZERO).lowerRate(BigDecimal.ZERO).build());

      assertThat(trades()).containsExactly(
        tuple(START.plusDays(1), "12", "밴드 위 매수", "83.33333333"),
        tuple(START.plusDays(2), "11", "밴드 아래 매도", "-83.33333333"));
    }

    @Test
    @DisplayName("밴드는 수정 종가로 보고 사는 값은 그 날짜 종가다")
    void bandUsesAdjustedClose() {
      // 종가는 늘 10, 수정 종가만 8 → 10 으로 올라 1일째 위 선(9.53) 위
      stubMarket(List.of(adjusted(-2, "10", "8"), adjusted(-1, "10", "8"), adjusted(0, "10", "8"),
        adjusted(1, "10", "10"), adjusted(2, "10", "10")));

      strategy.execute(request(2).build());

      assertThat(trades()).first().isEqualTo(tuple(START.plusDays(1), "10", "밴드 위 매수", "100"));
    }

    @Test
    @DisplayName("평균 기간 거래일이 안 찬 날은 밴드를 보지 않는다")
    void skipsDaysBeforeWindowFills() {
      // 1일째 30 은 앞 일봉이 하나라 보지 않고, 2일째 31 이 위 선(26.03) 위
      stubMarket(List.of(day(0, "10"), day(1, "30"), day(2, "31")));

      strategy.execute(request(2).build());

      assertThat(trades()).containsExactly(tuple(START.plusDays(2), "31", "밴드 위 매수", plain(new BigDecimal("32.25806452"))));
    }

    @Test
    @DisplayName("기간을 비우면 200 거래일 평균 · 위 · 아래 2.5% 로 보고, 시작일 전 200 거래일이 들어가게 더 받는다")
    void defaultBand() {
      // 시작일 앞 199일 10, 시작일 10.25 는 위 선(10.2513) 아래, 1일째 10.27 은 위 선(10.2527) 위
      List<OHLCPriceDto> prices = new ArrayList<>();
      for (int offset = -199; offset < 0; offset++) {
        prices.add(day(offset, "10"));
      }
      prices.add(day(0, "10.25"));
      prices.add(day(1, "10.27"));
      stubMarket(prices);

      strategy.execute(request(1).bandWindow(null).upperRate(null).lowerRate(null).build());

      assertThat(trades()).containsExactly(tuple(START.plusDays(1), "10.27", "밴드 위 매수", "97.37098345"));
      verify(marketDataClient).getOHLCPriceRange(SYMBOL, START.minusDays(314).toString(), START.plusDays(1).toString());
    }

    @Test
    @DisplayName("한 번도 위 선 위로 올라서지 않으면 투자 · 평가가 0 인 매수 0회 결과를 돌려주고 배당은 조회하지 않는다")
    void neverAboveReturnsZeroResult() {
      stubMarket(List.of(day(-2, "10"), day(-1, "10"), day(0, "10"), day(1, "10.5"), day(2, "9.5")));

      StrategyResponse response = strategy.execute(request(2).build());

      assertThat(executed()).isEmpty();
      assertThat(calculation().getTotalInvested()).isEqualByComparingTo("0");
      assertThat(response.getDailyValues()).isEmpty();
      verify(marketDataClient, never()).getDividendHistory(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("산 날 계산이 실패하면(환율 0) 사지 않고 계산 오류로 남긴 뒤 다음 날도 위 선 위면 원금으로 산다")
    void calculationFailureBuysNextDayAbove() {
      stubMarket(banded());
      stubDailyFx(7, Map.of(1, "0"));

      strategy.execute(request(7).purchaseFxRate(null).build());

      assertThat(skippedRounds()).containsExactly(new SkippedRound(START.plusDays(1), SkipReason.CALCULATION_ERROR));
      StrategyTransaction first = executed().getFirst();
      assertThat(first.getDate()).isEqualTo(START.plusDays(2));
      assertThat(first.getAmount()).isEqualByComparingTo("1300000");
      assertThat(first.getShares()).isEqualByComparingTo("45.45454545");
    }

    @Test
    @DisplayName("매매 통계에 매수 · 매도 수와 끝난 사이클의 승패 · 평균 손절 · 보유일, 들고 있는 사이클을 싣는다")
    void tradeStats() {
      stubMarket(banded());

      TradeStats stats = strategy.execute(request(7).build()).getTradeStats();

      // 1일째 1000달러로 사서 4일째 150달러 손실, 6일째 판 돈으로 다시 사서 들고 있음, 7일 동안 시작한 사이클 2
      assertThat(stats).usingRecursiveComparison()
        .withComparatorForType(Comparator.nullsFirst(BigDecimal::compareTo), BigDecimal.class)
        .isEqualTo(new TradeStats(2, 1, 1, true, 0, 1, BigDecimal.ZERO, null, new BigDecimal("15"), null,
          new BigDecimal("3"), new BigDecimal("104.36"), 0, 0));
    }

    @Test
    @DisplayName("양도세를 켜면 판 해의 원화 실현 이익을 싣고, 비우면 싣지 않는다")
    void capitalGainsTax() {
      stubMarket(banded());

      StrategyResponse response = strategy.execute(request(7).capitalGainsTax(true).build());

      // 130만원어치 50주를 850달러 × 1300원에 팔아 19만 5천원 손실
      assertThat(response.getCapitalGainsTax().years()).containsExactly(
        new CapitalGainsTax.Year(2024, new BigDecimal("-195000"), new BigDecimal("0"), new BigDecimal("0"), null,
          BigDecimal.ZERO));
      assertThat(strategy.execute(request(7).build()).getCapitalGainsTax()).isNull();
    }

    @Test
    @DisplayName("기간에 쓸 수 있는 시세가 하루도 없으면 DATA_NOT_FOUND")
    void noDataInPeriod() {
      given(marketDataClient.getOHLCPriceRange(eq(SYMBOL), anyString(), anyString()))
        .willReturn(List.of(day(-2, "10"), day(-1, "10"), day(0, "0")));

      assertThatThrownBy(() -> strategy.execute(request(1).build()))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.DATA_NOT_FOUND);
    }
  }

  private static final String SIGNAL = "^IDX";

  // 신호 종목의 시작일에서 offset 일 뒤 일봉
  private static OHLCPriceDto signalDay(int offset, String close) {
    BigDecimal price = new BigDecimal(close);
    return new OHLCPriceDto(SIGNAL, START.plusDays(offset), price, price, price, price, price, 1_000L, "USD", true);
  }

  @Nested
  @DisplayName("신호 종목 따로")
  class SignalSymbol {

    @Test
    @DisplayName("신호 종목을 주면 그 종목의 밴드로 들고 있을지 정하고 매매 종목이 쉬는 날 바뀐 것은 다음 거래일 종가에 사고판다")
    void tradesOnSignalSymbolBand() {
      // 신호는 banded() 값: 1 · 2일째 위 선 위, 3일째 사이, 4일째 아래 선 아래, 5일째 사이, 6 · 7일째 위 선 위
      List<OHLCPriceDto> signals = banded().stream()
        .map(candle -> signalDay((int) (candle.date().toEpochDay() - START.toEpochDay()), candle.closePrice().toPlainString()))
        .toList();
      // 매매 종목은 1 · 2 · 4일째가 쉬는 날
      given(marketDataClient.getOHLCPriceRange(eq(SIGNAL), anyString(), anyString())).willReturn(signals);
      stubMarket(List.of(day(0, "50"), day(3, "40"), day(5, "64"), day(6, "80"), day(7, "81")));

      StrategyResponse response = strategy.execute(request(7).signalSymbol(SIGNAL).build());

      assertThat(trades()).containsExactly(
        tuple(START.plusDays(3), "40", "밴드 위 매수", "25"),
        tuple(START.plusDays(5), "64", "밴드 아래 매도", "-25"),
        tuple(START.plusDays(6), "80", "밴드 위 매수", "20"));
      verify(marketDataClient).getOHLCPriceRange(SIGNAL, START.minusDays(18).toString(), START.plusDays(7).toString());
      verify(marketDataClient).getOHLCPriceRange(SYMBOL, START.toString(), START.plusDays(7).toString());
      SignalChart chart = response.getSignalChart();
      assertThat(chart.symbol()).isEqualTo(SIGNAL);
      assertThat(chart.points()).hasSize(8).first().isEqualTo(point(0, "10.00", "10.00", "11.00", "9.00"));
    }
  }

  // 시작일에서 offset 일 뒤 밴드 점(평균이 null 이면 세 칸 없음)
  private static SignalChart.Point point(int offset, String close, String average, String upper, String lower) {
    return SignalChart.Point.band(START.plusDays(offset), new BigDecimal(close),
      average == null ? null : new BigDecimal(average),
      upper == null ? null : new BigDecimal(upper),
      lower == null ? null : new BigDecimal(lower));
  }

  @Nested
  @DisplayName("밴드 차트")
  class Chart {

    @Test
    @DisplayName("시작일 ~ 평가일 일봉마다 수정 종가 · 평균 · 위 선 · 아래 선을 소수 둘째로 오래된 날부터 싣고 교차는 비운다")
    void pointsWithBand() {
      stubMarket(banded());

      SignalChart chart = strategy.execute(request(7).build()).getSignalChart();

      assertThat(chart.symbol()).isEqualTo(SYMBOL);
      assertThat(chart.points()).containsExactly(
        point(0, "10.00", "10.00", "11.00", "9.00"),
        point(1, "20.00", "13.33", "14.67", "12.00"),
        point(2, "22.00", "17.33", "19.07", "15.60"),
        point(3, "20.00", "20.67", "22.73", "18.60"),
        point(4, "17.00", "19.67", "21.63", "17.70"),
        point(5, "18.00", "18.33", "20.17", "16.50"),
        point(6, "25.00", "20.00", "22.00", "18.00"),
        point(7, "26.00", "23.00", "25.30", "20.70"));
      assertThat(chart.crosses()).isEmpty();
    }

    @Test
    @DisplayName("평균 기간 거래일이 안 찬 날은 평균 · 위 선 · 아래 선을 비운다")
    void blankBandBeforeWindowFills() {
      stubMarket(List.of(day(0, "10"), day(1, "30"), day(2, "31")));

      SignalChart chart = strategy.execute(request(2).build()).getSignalChart();

      assertThat(chart.points()).containsExactly(
        point(0, "10.00", null, null, null),
        point(1, "30.00", null, null, null),
        point(2, "31.00", "23.67", "26.03", "21.30"));
    }
  }

  // 요청 하나를 바꾼 경우 · 기대 오류 코드
  private static Arguments invalid(String label, UnaryOperator<MaBandStrategyRequest.MaBandStrategyRequestBuilder> change,
    BacktestResponse code) {
    return Arguments.of(label, change, code);
  }

  static Stream<Arguments> invalidRequests() {
    return Stream.of(
      invalid("종목 없음", b -> b.symbol(" "), BacktestResponse.STRATEGY_SYMBOL_REQUIRED),
      invalid("지수를 매매 종목으로", b -> b.symbol("^NDX"), BacktestResponse.INDEX_NOT_TRADABLE),
      invalid("시작일이 종료일 뒤", b -> b.startDate(START.plusDays(3)).endDate(START),
        BacktestResponse.STRATEGY_DATE_RANGE_INVALID),
      invalid("원금 없음", b -> b.investmentAmount(null), BacktestResponse.INVALID_INVESTMENT_AMOUNT),
      invalid("원금 0", b -> b.investmentAmount(BigDecimal.ZERO), BacktestResponse.INVALID_INVESTMENT_AMOUNT),
      invalid("평균 기간 1", b -> b.bandWindow(1), BacktestResponse.INVALID_BAND),
      invalid("평균 기간 251", b -> b.bandWindow(251), BacktestResponse.INVALID_BAND),
      invalid("위 비율 음수", b -> b.upperRate(new BigDecimal("-0.01")), BacktestResponse.INVALID_BAND),
      invalid("위 비율 1", b -> b.upperRate(BigDecimal.ONE), BacktestResponse.INVALID_BAND),
      invalid("아래 비율 음수", b -> b.lowerRate(new BigDecimal("-0.01")), BacktestResponse.INVALID_BAND),
      invalid("아래 비율 1", b -> b.lowerRate(BigDecimal.ONE), BacktestResponse.INVALID_BAND),
      invalid("배당 재투자", b -> b.reinvestDividends(true), BacktestResponse.SELL_WITH_REINVEST));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidRequests")
  @DisplayName("종목 · 기간 · 원금 · 밴드 기간 · 비율이 맞지 않거나 배당 재투자를 켜면 시세를 받기 전에 막는다")
  void rejects(String label, UnaryOperator<MaBandStrategyRequest.MaBandStrategyRequestBuilder> change,
    BacktestResponse code) {
    MaBandStrategyRequest request = change.apply(request(7)).build();

    assertThatThrownBy(() -> strategy.execute(request))
      .isInstanceOf(BacktestException.class)
      .extracting("code")
      .isEqualTo(code);
    verifyNoInteractions(marketDataClient);
  }

  @Test
  @DisplayName("평균 기간 250 · 위 · 아래 비율 0.99 는 통과한다")
  void acceptsUpperBounds() {
    stubMarket(banded());

    strategy.execute(request(7).bandWindow(250).upperRate(new BigDecimal("0.99")).lowerRate(new BigDecimal("0.99"))
      .build());

    assertThat(executed()).isEmpty();
  }

  @Test
  @DisplayName("요청이 null 이면 STRATEGY_REQUEST_NULL")
  void rejectsNull() {
    assertThatThrownBy(() -> strategy.execute(null))
      .isInstanceOf(BacktestException.class)
      .extracting("code")
      .isEqualTo(BacktestResponse.STRATEGY_REQUEST_NULL);
  }

  @Test
  @DisplayName("이동평균 교차 요청이 오면 전략 타입 불일치로 막는다")
  void rejectsOtherRequestType() {
    MaCrossStrategyRequest other = MaCrossStrategyRequest.builder().symbol("AAPL")
      .startDate(LocalDate.of(2023, 1, 1)).endDate(LocalDate.of(2024, 1, 1)).build();

    assertThatThrownBy(() -> strategy.execute(other))
      .isInstanceOf(BacktestException.class)
      .extracting("code")
      .isEqualTo(BacktestResponse.STRATEGY_TYPE_MISMATCH);
    verifyNoInteractions(marketDataClient);
  }
}
