package com.muscat.backtest.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.never;

import com.muscat.backtest.common.calculation.StrategyCalculationResult;
import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.PriceDirection;
import com.muscat.backtest.common.enums.type.SkipReason;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.SkippedRound;
import com.muscat.backtest.domain.model.SkippedSpan;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import com.muscat.commonlib.dto.StockPriceDto;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConditionalPurchaseStrategy 테스트")
class ConditionalPurchaseStrategyTest {

  @Mock
  private MarketDataClientWrapper marketDataClient;

  @Mock
  private ResponseMapper responseMapper;

  private ConditionalPurchaseStrategy strategy;

  @BeforeEach
  void setUp() {
    strategy = new ConditionalPurchaseStrategy(marketDataClient, responseMapper);
    lenient().when(marketDataClient.defaultFxRate()).thenReturn(new BigDecimal("1350"));
  }

  @Nested
  @DisplayName("전략 타입 테스트")
  class StrategyTypeTests {

    @Test
    @DisplayName("전략 타입은 CONDITIONAL_PURCHASE")
    void getStrategyType_ReturnsConditionalPurchase() {
      assertThat(strategy.getStrategyType()).isEqualTo(StrategyType.CONDITIONAL_PURCHASE);
    }
  }

  @Nested
  @DisplayName("분할 조정 종가 판정")
  class AdjustedCloseTriggerTests {

    @Test
    @DisplayName("분할일 가격 변화를 하락으로 보고 사지 않는다 - closePrice 가 분할 조정된 값이라")
    void execute_SplitDay_NoPhantomDropBuy() {
      // given: 20% 하락시 매수, 최대 5회
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .userId("test-user")
        .symbol("SPLIT-TEST")
        .startDate(LocalDate.of(2024, 1, 15))
        .endDate(LocalDate.of(2024, 1, 16))
        .amountPerPurchase(new BigDecimal("1000.00"))
        .maxPurchases(5)
        .dropPercentage(new BigDecimal("0.2")) // 20%
        .build();

      // 수집 단계에서 분할이 close 에 반영돼 옴(분할 전후로 25 로 평탄)
      OHLCPriceDto day1 = new OHLCPriceDto("SPLIT-TEST", LocalDate.of(2024, 1, 15),
        new BigDecimal("24"), new BigDecimal("26"), new BigDecimal("23"),
        new BigDecimal("25.00"), new BigDecimal("24.00"), 1_000_000L, "USD", true);
      OHLCPriceDto day2 = new OHLCPriceDto("SPLIT-TEST", LocalDate.of(2024, 1, 16),
        new BigDecimal("24"), new BigDecimal("26"), new BigDecimal("23"),
        new BigDecimal("25.00"), new BigDecimal("25.00"), 1_000_000L, "USD", true);
      given(marketDataClient.getOHLCPriceRange(eq("SPLIT-TEST"), eq("2024-01-15"), eq("2024-01-16")))
        .willReturn(List.of(day1, day2));
      given(marketDataClient.getBulkFxRates(any()))
        .willReturn(java.util.Map.of(
          "2024-01-15", new BigDecimal("1300.00"),
          "2024-01-16", new BigDecimal("1300.00")));
      given(marketDataClient.getFxRate(anyString()))
        .willReturn(new FxRateDto(LocalDate.now(), new BigDecimal("1300.00")));
      given(marketDataClient.getOHLCPrice(eq("SPLIT-TEST"), anyString()))
        .willReturn(day2);
      given(responseMapper.toStrategyResponse(any(ConditionalStrategyRequest.class), any(), any(), any(), any()))
        .willReturn(StrategyResponse.builder().strategyType(StrategyType.CONDITIONAL_PURCHASE).build());

      // when
      strategy.execute(request);

      // then: 분할일에 사지 않아 거래 0건
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<StrategyTransaction>> captor = ArgumentCaptor.forClass(List.class);
      verify(responseMapper).toStrategyResponse(
        any(ConditionalStrategyRequest.class), captor.capture(), any(), any(), any());
      assertThat(captor.getValue()).isEmpty();
    }
  }

  @Nested
  @DisplayName("수수료")
  class FeeTests {

    @Test
    @DisplayName("매수마다 수수료를 차감하고 남은 달러로 산다")
    void execute_DeductsFee() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .userId("test-user")
        .symbol("FEE-TEST")
        .startDate(LocalDate.of(2024, 1, 15))
        .endDate(LocalDate.of(2024, 1, 16))
        .amountPerPurchase(new BigDecimal("1300000"))
        .maxPurchases(1)
        .dropPercentage(new BigDecimal("0.2"))
        .tradingFeeRate(new BigDecimal("0.01"))
        .build();

      // 첫날 종가 125 의 20% 하락 기준가 100 에 시가부터 닿아 100 에 삼
      OHLCPriceDto day0 = new OHLCPriceDto("FEE-TEST", LocalDate.of(2024, 1, 15),
        new BigDecimal("125"), new BigDecimal("125"), new BigDecimal("125"),
        new BigDecimal("125.00"), new BigDecimal("125.00"), 1_000_000L, "USD", true);
      OHLCPriceDto day1 = new OHLCPriceDto("FEE-TEST", LocalDate.of(2024, 1, 16),
        new BigDecimal("100"), new BigDecimal("101"), new BigDecimal("98"),
        new BigDecimal("100.00"), new BigDecimal("100.00"), 1_000_000L, "USD", true);
      given(marketDataClient.getOHLCPriceRange(eq("FEE-TEST"), eq("2024-01-15"), eq("2024-01-16")))
        .willReturn(List.of(day0, day1));
      given(marketDataClient.getBulkFxRates(any()))
        .willReturn(java.util.Map.of(
          "2024-01-15", new BigDecimal("1300"),
          "2024-01-16", new BigDecimal("1300")));
      given(marketDataClient.getFxRate(anyString()))
        .willReturn(new FxRateDto(LocalDate.now(), new BigDecimal("1300")));
      given(marketDataClient.getOHLCPrice(eq("FEE-TEST"), anyString()))
        .willReturn(day1);
      given(marketDataClient.getDividendHistory(eq("FEE-TEST"), anyString(), anyString()))
        .willReturn(java.util.Collections.emptyList());
      given(responseMapper.toStrategyResponse(any(ConditionalStrategyRequest.class), any(), any(), any(), any()))
        .willReturn(StrategyResponse.builder().build());

      strategy.execute(request);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<StrategyTransaction>> captor = ArgumentCaptor.forClass(List.class);
      verify(responseMapper).toStrategyResponse(
        any(ConditionalStrategyRequest.class), captor.capture(), any(), any(), any());
      assertThat(captor.getValue()).singleElement().satisfies(tx -> {
        assertThat(tx.getFee()).isEqualByComparingTo("10.00");
        assertThat(tx.getShares()).isEqualByComparingTo("9.9");
      });
    }
  }

  @Nested
  @DisplayName("요청 검증 테스트")
  class ValidationTests {

    @Test
    @DisplayName("요청이 null이면 예외 발생")
    void validateRequest_Null_ThrowsException() {
      assertThatThrownBy(() -> strategy.execute(null))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.STRATEGY_REQUEST_NULL);
    }

    @Test
    @DisplayName("심볼이 null이면 예외 발생")
    void validateRequest_NullSymbol_ThrowsException() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol(null)
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .amountPerPurchase(BigDecimal.valueOf(10000))
        .dropPercentage(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.execute(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.STRATEGY_SYMBOL_REQUIRED);
    }

    @Test
    @DisplayName("심볼이 빈 문자열이면 예외 발생")
    void validateRequest_EmptySymbol_ThrowsException() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol("   ")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .amountPerPurchase(BigDecimal.valueOf(10000))
        .dropPercentage(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.execute(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.STRATEGY_SYMBOL_REQUIRED);
    }

    @Test
    @DisplayName("시작일이 null이면 예외 발생")
    void validateRequest_NullStartDate_ThrowsException() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(null)
        .endDate(LocalDate.of(2023, 12, 31))
        .amountPerPurchase(BigDecimal.valueOf(10000))
        .dropPercentage(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.execute(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.STRATEGY_START_DATE_REQUIRED);
    }

    @Test
    @DisplayName("종료일이 null이면 예외 발생")
    void validateRequest_NullEndDate_ThrowsException() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(null)
        .amountPerPurchase(BigDecimal.valueOf(10000))
        .dropPercentage(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.execute(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.STRATEGY_END_DATE_REQUIRED);
    }

    @Test
    @DisplayName("시작일이 종료일보다 이후면 예외 발생")
    void validateRequest_InvalidDateRange_ThrowsException() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 12, 31))
        .endDate(LocalDate.of(2023, 1, 1))
        .amountPerPurchase(BigDecimal.valueOf(10000))
        .dropPercentage(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.execute(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.STRATEGY_DATE_RANGE_INVALID);
    }

    // 검사를 모두 통과하는 요청(회당 금액만, 예산 · 최대 횟수 없음)
    private ConditionalStrategyRequest.ConditionalStrategyRequestBuilder valid() {
      return ConditionalStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .amountPerPurchase(BigDecimal.valueOf(10000))
        .dropPercentage(BigDecimal.valueOf(0.1));
    }

    private void assertRejected(ConditionalStrategyRequest request, BacktestResponse code) {
      assertThatThrownBy(() -> strategy.execute(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(code);
    }

    @Test
    @DisplayName("회당 금액 · 주식 수가 둘 다 없거나 둘 다 있으면 예외 발생")
    void validateRequest_AmountOrShares_ThrowsException() {
      assertRejected(valid().amountPerPurchase(null).build(), BacktestResponse.AMOUNT_OR_SHARES_REQUIRED);
      assertRejected(valid().sharesPerPurchase(1).build(), BacktestResponse.AMOUNT_OR_SHARES_REQUIRED);
    }

    @Test
    @DisplayName("주식 수가 1 보다 작으면 예외 발생")
    void validateRequest_ZeroShares_ThrowsException() {
      assertRejected(valid().amountPerPurchase(null).sharesPerPurchase(0).build(),
        BacktestResponse.INVALID_SHARES_PER_PURCHASE);
    }

    @Test
    @DisplayName("회당 금액이 0 이하면 예외 발생")
    void validateRequest_ZeroAmountPerPurchase_ThrowsException() {
      assertRejected(valid().amountPerPurchase(BigDecimal.ZERO).build(),
        BacktestResponse.STRATEGY_AMOUNT_PER_PURCHASE_REQUIRED);
    }

    @Test
    @DisplayName("예산을 주면 0 보다 커야 하고, 0 이하면 예외 발생")
    void validateRequest_ZeroBudget_ThrowsException() {
      assertRejected(valid().totalInvestment(BigDecimal.ZERO).build(), BacktestResponse.INVALID_TOTAL_INVESTMENT);
    }

    @Test
    @DisplayName("dropPercentage가 null이면 예외 발생")
    void validateRequest_NullDropPercentage_ThrowsException() {
      assertRejected(valid().dropPercentage(null).build(), BacktestResponse.STRATEGY_DROP_PERCENTAGE_REQUIRED);
    }

    @Test
    @DisplayName("dropPercentage가 0 이하면 예외 발생")
    void validateRequest_ZeroDropPercentage_ThrowsException() {
      assertRejected(valid().dropPercentage(BigDecimal.ZERO).build(), BacktestResponse.STRATEGY_DROP_PERCENTAGE_REQUIRED);
    }

    @Test
    @DisplayName("maxPurchases가 0 이하면 예외 발생")
    void validateRequest_ZeroMaxPurchases_ThrowsException() {
      assertRejected(valid().maxPurchases(0).build(), BacktestResponse.INVALID_MAX_PURCHASES);
    }
  }

  @Nested
  @DisplayName("하락 · 상승 판정 · 한도")
  class DropTriggerTests {

    private static final String SYMBOL = "DROP";
    private static final LocalDate START = LocalDate.of(2024, 3, 4);

    private OHLCPriceDto day(int offset, String close) {
      BigDecimal price = new BigDecimal(close);
      return new OHLCPriceDto(SYMBOL, START.plusDays(offset), price, price, price, price, price, 1_000L, "USD", true);
    }

    // 회당 130만원(환율 1300 이면 1000달러), 전날 종가 대비 장중 10% 하락 시 매수
    private ConditionalStrategyRequest.ConditionalStrategyRequestBuilder perPurchase(int days, int maxPurchases) {
      return ConditionalStrategyRequest.builder().userId("test-user").symbol(SYMBOL)
        .startDate(START).endDate(START.plusDays(days - 1))
        .amountPerPurchase(new BigDecimal("1300000"))
        .maxPurchases(maxPurchases).dropPercentage(new BigDecimal("0.1"));
    }

    // 기간 시세 · 평가일(과거) 시세와 환율 · 배당(일별 환율은 빈 응답이라 기본 환율)
    private void stubMarket(ConditionalStrategyRequest request, List<OHLCPriceDto> prices, boolean dailyFx) {
      given(marketDataClient.getOHLCPriceRange(SYMBOL, request.getStartDate().toString(), request.getEndDate().toString()))
        .willReturn(prices);
      if (dailyFx) {
        given(marketDataClient.getBulkFxRates(any())).willReturn(Map.of());
      }
      given(marketDataClient.getOHLCPrice(eq(SYMBOL), anyString())).willReturn(prices.getLast());
      given(marketDataClient.getFxRate(anyString())).willReturn(new FxRateDto(request.getEndDate(), new BigDecimal("1300")));
      // 매수 0회면 배당을 조회하지 않음
      lenient().when(marketDataClient.getDividendHistory(eq(SYMBOL), anyString(), anyString())).thenReturn(List.of());
      given(responseMapper.toStrategyResponse(any(ConditionalStrategyRequest.class), any(), any(), any(), any()))
        .willReturn(StrategyResponse.builder().build());
    }

    private StrategyCalculationResult calculation() {
      ArgumentCaptor<StrategyCalculationResult> captor = ArgumentCaptor.forClass(StrategyCalculationResult.class);
      verify(responseMapper).toStrategyResponse(any(ConditionalStrategyRequest.class), any(), captor.capture(), any(), any());
      return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<SkippedRound> skippedRounds() {
      ArgumentCaptor<List<SkippedRound>> captor = ArgumentCaptor.forClass(List.class);
      verify(responseMapper).toStrategyResponse(any(ConditionalStrategyRequest.class), any(), any(), any(), captor.capture());
      return captor.getValue();
    }

    private List<StrategyTransaction> executed() {
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<StrategyTransaction>> captor = ArgumentCaptor.forClass(List.class);
      verify(responseMapper).toStrategyResponse(any(ConditionalStrategyRequest.class), captor.capture(), any(), any(), any());
      return captor.getValue();
    }

    // 시가 · 저가 · 종가(고가는 시가와 종가 중 큰 값), 값이 null 이면 그 칸 없음
    private OHLCPriceDto bar(int offset, String open, String low, String close) {
      BigDecimal o = open == null ? null : new BigDecimal(open);
      BigDecimal c = new BigDecimal(close);
      BigDecimal high = o == null ? c : o.max(c);
      return new OHLCPriceDto(SYMBOL, START.plusDays(offset), o, high, low == null ? null : new BigDecimal(low), c, c,
        1_000L, "USD", true);
    }

    private OHLCPriceDto candle(int offset, String open, String high, String low, String close) {
      BigDecimal c = new BigDecimal(close);
      return new OHLCPriceDto(SYMBOL, START.plusDays(offset), new BigDecimal(open), high == null ? null : new BigDecimal(high),
        new BigDecimal(low), c, c, 1_000L, "USD", true);
    }

    private List<Tuple> priceAndTrigger() {
      return executed().stream().map(tx -> tuple(tx.getPrice().stripTrailingZeros().toPlainString(), tx.getTrigger())).toList();
    }

    @Test
    @DisplayName("장중 저가가 전날 종가 × (1 - 하락률) 에 닿으면 그 값에 산다")
    void buysAtTriggerWhenLowTouches() {
      ConditionalStrategyRequest request = perPurchase(2, 10).build();
      // 전날 종가 100, 기준가 90, 시가 99 · 저가 89 · 종가 97
      stubMarket(request, List.of(day(0, "100"), bar(1, "99", "89", "97")), true);

      strategy.execute(request);

      assertThat(priceAndTrigger()).containsExactly(
        tuple("90", "10.0%하락"));
    }

    @Test
    @DisplayName("상승이면 장중 고가가 전날 종가 × (1 + 상승률) 에 닿을 때 그 값에 산다")
    void risingBuysAtTrigger() {
      ConditionalStrategyRequest request = perPurchase(3, 10).direction(PriceDirection.RISE).build();
      // 2일째 전날 100 → 기준가 110 · 고가 111, 3일째 전날 105 → 기준가 115.5 · 고가 115 안 삼
      stubMarket(request, List.of(day(0, "100"), candle(1, "101", "111", "100", "105"),
        candle(2, "106", "115", "104", "110")), true);

      strategy.execute(request);

      assertThat(priceAndTrigger()).containsExactly(
        tuple("110", "10.0%상승"));
    }

    @Test
    @DisplayName("상승이면 시가부터 기준가 위일 때 시가에 산다")
    void risingBuysAtOpenOnGapUp() {
      ConditionalStrategyRequest request = perPurchase(2, 10).direction(PriceDirection.RISE).build();
      stubMarket(request, List.of(day(0, "100"), candle(1, "115", "118", "113", "116")), true);

      strategy.execute(request);

      assertThat(priceAndTrigger()).containsExactly(
        tuple("115", "15.0%상승"));
    }

    @Test
    @DisplayName("상승이면 떨어진 날은 사지 않는다")
    void risingIgnoresDrop() {
      ConditionalStrategyRequest request = perPurchase(2, 10).direction(PriceDirection.RISE).build();
      stubMarket(request, List.of(day(0, "100"), candle(1, "95", "96", "80", "85")), true);

      strategy.execute(request);

      assertThat(executed()).isEmpty();
    }

    @Test
    @DisplayName("상승이면 고가가 없는 날은 종가로 판정해 종가에 산다")
    void risingMissingHighUsesClose() {
      ConditionalStrategyRequest request = perPurchase(2, 10).direction(PriceDirection.RISE).build();
      stubMarket(request, List.of(day(0, "100"), candle(1, "101", null, "100", "112")), true);

      strategy.execute(request);

      assertThat(priceAndTrigger()).containsExactly(
        tuple("112", "12.0%상승"));
    }

    @Test
    @DisplayName("둘 다면 떨어진 날과 오른 날 모두 산다")
    void bothBuysOnDropAndRise() {
      ConditionalStrategyRequest request = perPurchase(3, 10).direction(PriceDirection.BOTH).build();
      // 2일째 전날 100 → 하락 기준가 90 · 저가 89, 3일째 전날 92 → 상승 기준가 101.2 · 고가 102
      stubMarket(request, List.of(day(0, "100"), candle(1, "99", "100", "89", "92"),
        candle(2, "93", "102", "92", "101")), true);

      strategy.execute(request);

      assertThat(priceAndTrigger()).containsExactly(
        tuple("90", "10.0%하락"),
        tuple("101.2", "10.0%상승"));
    }

    @Test
    @DisplayName("둘 다에서 같은 날 두 기준가에 모두 닿으면 상승 기준가에 한 번 산다")
    void bothTouchedSameDayBuysAtRiseOnce() {
      ConditionalStrategyRequest request = perPurchase(2, 10).direction(PriceDirection.BOTH).build();
      stubMarket(request, List.of(day(0, "100"), candle(1, "100", "111", "89", "100")), true);

      strategy.execute(request);

      assertThat(priceAndTrigger()).containsExactly(
        tuple("110", "10.0%상승"));
    }

    @Test
    @DisplayName("둘 다에서 시가부터 하락 기준가 아래면 고가가 상승 기준가에 닿아도 시가에 하락으로 산다")
    void bothGapDownBuysAtOpen() {
      ConditionalStrategyRequest request = perPurchase(2, 10).direction(PriceDirection.BOTH).build();
      stubMarket(request, List.of(day(0, "100"), candle(1, "88", "111", "87", "105")), true);

      strategy.execute(request);

      assertThat(priceAndTrigger()).containsExactly(
        tuple("88", "12.0%하락"));
    }

    @Test
    @DisplayName("방향을 비우면 하락으로 본다")
    void nullDirectionMeansDrop() {
      ConditionalStrategyRequest request = perPurchase(2, 10).direction(null).build();
      stubMarket(request, List.of(day(0, "100"), candle(1, "99", "115", "89", "97")), true);

      strategy.execute(request);

      assertThat(priceAndTrigger()).containsExactly(
        tuple("90", "10.0%하락"));
    }

    @Test
    @DisplayName("시가부터 기준가 아래면 시가에 산다")
    void buysAtOpenOnGapDown() {
      ConditionalStrategyRequest request = perPurchase(2, 10).build();
      stubMarket(request, List.of(day(0, "100"), bar(1, "85", "84", "88")), true);

      strategy.execute(request);

      assertThat(priceAndTrigger()).containsExactly(
        tuple("85", "15.0%하락"));
    }

    @Test
    @DisplayName("저가가 기준가보다 훨씬 내려가도 그 날짜에는 한 번만 산다")
    void buysOncePerDay() {
      ConditionalStrategyRequest request = perPurchase(2, 10).build();
      // 기준가 90, 저가 70(하락률 두 배보다 깊음)
      stubMarket(request, List.of(day(0, "100"), bar(1, "99", "70", "75")), true);

      strategy.execute(request);

      assertThat(priceAndTrigger()).containsExactly(
        tuple("90", "10.0%하락"));
    }

    @Test
    @DisplayName("저가가 기준가와 같으면 사고, 기준가보다 높으면 사지 않는다")
    void triggerBoundary() {
      ConditionalStrategyRequest request = perPurchase(3, 10).build();
      // 2일째 전날 100 → 기준가 90 · 저가 90 매수, 3일째 전날 95 → 기준가 85.5 · 저가 86 안 삼
      stubMarket(request, List.of(day(0, "100"), bar(1, "98", "90", "95"), bar(2, "94", "86", "92")), true);

      strategy.execute(request);

      assertThat(priceAndTrigger()).containsExactly(
        tuple("90", "10.0%하락"));
    }

    @Test
    @DisplayName("직전 매수가보다 올랐어도 전날 종가보다 장중에 떨어지면 산다")
    void buysOnIntradayDropAfterRise() {
      ConditionalStrategyRequest request = perPurchase(3, 10).build();
      // 100 매수 → 120 → 3일째 전날 120, 기준가 108, 저가 107(직전 매수가 100 보다는 위)
      stubMarket(request, List.of(day(0, "100"), day(1, "120"), bar(2, "118", "107", "115")), true);

      strategy.execute(request);

      assertThat(priceAndTrigger()).containsExactly(
        tuple("108", "10.0%하락"));
    }

    @Test
    @DisplayName("시세가 깨진 날 다음 날은 그 앞 거래일 종가로 기준가를 잡는다")
    void brokenDayKeepsPreviousClose() {
      ConditionalStrategyRequest request = perPurchase(3, 10).build();
      stubMarket(request, List.of(day(0, "100"), day(1, "0"), bar(2, "95", "89", "92")), true);

      strategy.execute(request);

      assertThat(priceAndTrigger()).containsExactly(
        tuple("90", "10.0%하락"));
      assertThat(skippedRounds()).containsExactly(new SkippedRound(START.plusDays(1), SkipReason.NO_PRICE));
    }

    @Test
    @DisplayName("저가가 없는 날은 시가를 보지 않고 종가로 판정해 종가에 산다")
    void missingLowUsesClose() {
      ConditionalStrategyRequest request = perPurchase(4, 10).build();
      // 3일째 저가 · 시가 없음, 종가 107(기준가 108 아래), 4일째 저가 없음 · 시가 90 · 종가 100(기준가 96.3 위)
      stubMarket(request, List.of(day(0, "100"), day(1, "120"), bar(2, null, null, "107"), bar(3, "90", null, "100")),
        true);

      strategy.execute(request);

      assertThat(priceAndTrigger()).containsExactly(
        tuple("107", "10.8%하락"));
    }

    @Test
    @DisplayName("종가가 조정종가뿐인 날은 시가 · 저가를 보지 않는다")
    void adjustedCloseOnlyIgnoresRange() {
      ConditionalStrategyRequest request = perPurchase(2, 10).build();
      OHLCPriceDto adjustedOnly = new OHLCPriceDto(SYMBOL, START.plusDays(1), new BigDecimal("80"),
        new BigDecimal("96"), new BigDecimal("80"), null, new BigDecimal("95"), 1_000L, "USD", true);
      stubMarket(request, List.of(day(0, "100"), adjustedOnly), true);

      strategy.execute(request);

      assertThat(executed()).isEmpty();
    }

    @Test
    @DisplayName("시가만 없는 날은 기준가에 산다")
    void missingOpenBuysAtTrigger() {
      ConditionalStrategyRequest request = perPurchase(2, 10).build();
      stubMarket(request, List.of(day(0, "100"), bar(1, null, "80", "85")), true);

      strategy.execute(request);

      assertThat(priceAndTrigger()).containsExactly(
        tuple("90", "10.0%하락"));
    }

    @Test
    @DisplayName("매수가로 주식 수를 센다")
    void sharesUseTriggerPrice() {
      ConditionalStrategyRequest request = perPurchase(2, 10).build();
      stubMarket(request, List.of(day(0, "100"), bar(1, "99", "89", "97")), true);

      strategy.execute(request);

      // 회당 130만원 · 기본 환율 1350 · 매수가 90
      assertThat(executed().getFirst().getShares()).isEqualByComparingTo(
        BacktestCalculationUtils.calculateShares(new BigDecimal("1300000"), new BigDecimal("1350"), new BigDecimal("90")));
    }

    @Test
    @DisplayName("오른 날은 기준 이상 올라도 사지 않는다")
    void noBuyOnRise() {
      ConditionalStrategyRequest request = perPurchase(3, 10).build();
      stubMarket(request, List.of(day(0, "100"), day(1, "120"), day(2, "130")), true);

      strategy.execute(request);

      assertThat(executed()).isEmpty();
    }

    // 첫날 100 뒤로 날마다 전날 종가보다 20% 넘게 내려 매번 조건이 맞는 시세
    private List<OHLCPriceDto> falling() {
      return List.of(day(0, "100"), day(1, "80"), day(2, "64"), day(3, "50"), day(4, "40"), day(5, "32"));
    }

    @Test
    @DisplayName("예산 안에서 회당 금액만큼 사고, 다음 회차가 예산을 넘으면 그 날짜부터 끝까지 예산 부족 구간 한 줄로 남긴다")
    void budgetShortfallSpan() {
      ConditionalStrategyRequest request = perPurchase(6, 10).maxPurchases(null)
        .totalInvestment(new BigDecimal("3900000")).build();
      stubMarket(request, falling(), true);

      StrategyResponse response = strategy.execute(request);

      assertThat(executed()).hasSize(3).allSatisfy(tx -> assertThat(tx.getAmount()).isEqualByComparingTo("1300000"));
      assertThat(response.getSkippedSpan()).isEqualTo(new SkippedSpan(START.plusDays(4), 2, SkipReason.OVER_BUDGET));
      assertThat(skippedRounds()).isEmpty();
    }

    @Test
    @DisplayName("예산 · 최대 횟수를 모두 비우면 조건이 맞는 날마다 끝까지 산다")
    void noLimitsBuysEveryTrigger() {
      ConditionalStrategyRequest request = perPurchase(6, 10).maxPurchases(null).build();
      stubMarket(request, falling(), true);

      StrategyResponse response = strategy.execute(request);

      assertThat(executed()).hasSize(5);
      assertThat(response.getSkippedSpan()).isNull();
    }

    @Test
    @DisplayName("최대 횟수가 먼저 닿으면 거기서 멈추고 예산 부족은 남기지 않는다")
    void maxPurchasesFirst() {
      ConditionalStrategyRequest request = perPurchase(6, 2).totalInvestment(new BigDecimal("13000000")).build();
      stubMarket(request, falling(), true);

      StrategyResponse response = strategy.execute(request);

      assertThat(executed()).hasSize(2);
      assertThat(response.getSkippedSpan()).isNull();
    }

    @Test
    @DisplayName("예산이 먼저 닿으면 최대 횟수까지 남은 회차만 예산 부족으로 센다")
    void budgetFirstCountsUpToMax() {
      ConditionalStrategyRequest request = perPurchase(6, 3).totalInvestment(new BigDecimal("2600000")).build();
      stubMarket(request, falling(), true);

      StrategyResponse response = strategy.execute(request);

      assertThat(executed()).hasSize(2);
      assertThat(response.getSkippedSpan()).isEqualTo(new SkippedSpan(START.plusDays(3), 1, SkipReason.OVER_BUDGET));
    }

    @Test
    @DisplayName("예산이 회당 금액보다 작으면 매수 0회 결과에 첫 조건 날짜부터의 예산 부족 구간을 싣는다")
    void budgetBelowAmountZeroResult() {
      ConditionalStrategyRequest request = perPurchase(4, 10).maxPurchases(null)
        .totalInvestment(new BigDecimal("1000000")).build();
      stubMarket(request, falling().subList(0, 4), true);

      StrategyResponse response = strategy.execute(request);

      assertThat(executed()).isEmpty();
      assertThat(calculation().getTotalInvested()).isEqualByComparingTo("0");
      assertThat(response.getSkippedSpan()).isEqualTo(new SkippedSpan(START.plusDays(1), 3, SkipReason.OVER_BUDGET));
    }

    @Test
    @DisplayName("주식 수로 사면 그 주식 수를 사고 매수가 × 주식 수 + 수수료를 그 날짜 환율로 바꾼 돈을 넣는다")
    void buysSharesPerPurchase() {
      ConditionalStrategyRequest request = perPurchase(2, 10).amountPerPurchase(null).sharesPerPurchase(3)
        .tradingFeeRate(new BigDecimal("0.01")).build();
      stubMarket(request, List.of(day(0, "100"), bar(1, "99", "89", "97")), true);

      strategy.execute(request);

      // 매수가 90 × 3주 = 270달러 + 수수료 2.70달러, 기본 환율 1350
      assertThat(executed()).singleElement().satisfies(tx -> {
        assertThat(tx.getShares()).isEqualByComparingTo("3");
        assertThat(tx.getFee()).isEqualByComparingTo("2.70");
        assertThat(tx.getAmount()).isEqualByComparingTo("368145");
        assertThat(tx.getTrigger()).isEqualTo("10.0%하락");
      });
    }

    @Test
    @DisplayName("주식 수로 사면 예산이 모자란 날짜부터는 값이 내린 날도 사지 않고 끝까지 예산 부족으로 센다")
    void sharesShortfallSpanToEnd() {
      ConditionalStrategyRequest request = perPurchase(5, 10).maxPurchases(null).amountPerPurchase(null)
        .sharesPerPurchase(1).totalInvestment(new BigDecimal("300000")).build();
      // 2일째 80(10만 8천원) 뒤 4일째 170(22만 9,500원)은 남은 예산 19만 2천원을 넘고 5일째 100(13만 5천원)은 남은 예산 안
      stubMarket(request, List.of(day(0, "100"), day(1, "80"), day(2, "200"), day(3, "170"), day(4, "100")), true);

      StrategyResponse response = strategy.execute(request);

      assertThat(executed()).singleElement().satisfies(tx -> assertThat(tx.getAmount()).isEqualByComparingTo("108000"));
      assertThat(response.getSkippedSpan()).isEqualTo(new SkippedSpan(START.plusDays(3), 2, SkipReason.OVER_BUDGET));
    }

    @Test
    @DisplayName("매수 환율을 직접 주면 일별 환율을 조회하지 않고 그 값으로 산다")
    void manualPurchaseFxRate() {
      ConditionalStrategyRequest request = perPurchase(2, 10).purchaseFxRate(new BigDecimal("1200")).build();
      stubMarket(request, List.of(day(0, "100"), day(1, "80")), false);

      strategy.execute(request);

      assertThat(executed()).singleElement().extracting(StrategyTransaction::getFxRate)
        .satisfies(rate -> assertThat(rate).isEqualByComparingTo("1200"));
      verify(marketDataClient, never()).getBulkFxRates(any());
    }

    @Test
    @DisplayName("종가가 0 이하인 깨진 날은 시세 없음으로 세고 다음 거래일 종가를 기준가로 잡는다")
    void skipsBrokenPriceDay() {
      ConditionalStrategyRequest request = perPurchase(3, 10).build();
      stubMarket(request, List.of(day(0, "0"), day(1, "100"), day(2, "80")), true);

      strategy.execute(request);

      assertThat(executed()).singleElement().satisfies(tx -> {
        assertThat(tx.getDate()).isEqualTo(START.plusDays(2));
        assertThat(tx.getTrigger()).isEqualTo("20.0%하락");
      });
      assertThat(skippedRounds()).containsExactly(new SkippedRound(START, SkipReason.NO_PRICE));
    }

    @Test
    @DisplayName("살 날의 계산이 실패하면(환율 0) 사지 않고 계산 오류로 남긴 뒤 다음 조건에 산다")
    void countsCalculationFailure() {
      ConditionalStrategyRequest request = perPurchase(3, 10).build();
      stubMarket(request, List.of(day(0, "100"), day(1, "80"), day(2, "64")), false);
      given(marketDataClient.getBulkFxRates(any())).willReturn(Map.of(START.plusDays(1).toString(), BigDecimal.ZERO));

      strategy.execute(request);

      assertThat(executed()).singleElement().satisfies(tx -> assertThat(tx.getDate()).isEqualTo(START.plusDays(2)));
      assertThat(skippedRounds()).containsExactly(new SkippedRound(START.plusDays(1), SkipReason.CALCULATION_ERROR));
    }

    @Test
    @DisplayName("기간 첫 거래일은 크게 내려도 사지 않고 기준 종가로만 쓴다")
    void firstTradingDayIsReferenceOnly() {
      ConditionalStrategyRequest request = perPurchase(2, 10).build();
      // 첫날 시가 100 · 저가 50, 둘째 날은 첫날 종가 60 의 기준가 54 위
      stubMarket(request, List.of(bar(0, "100", "50", "60"), bar(1, "60", "58", "59")), true);

      strategy.execute(request);

      assertThat(executed()).isEmpty();
    }

    @Test
    @DisplayName("조건이 한 번도 맞지 않으면 투자 · 평가 · 수익이 0 인 매수 0회 결과를 돌려준다")
    void noTriggerReturnsZeroResult() {
      ConditionalStrategyRequest request = perPurchase(3, 10).build();
      stubMarket(request, List.of(day(0, "100"), day(1, "101"), day(2, "102")), true);

      StrategyResponse response = strategy.execute(request);

      assertThat(executed()).isEmpty();
      StrategyCalculationResult calculation = calculation();
      assertThat(calculation.getTotalInvested()).isEqualByComparingTo("0");
      assertThat(calculation.getTotalShares()).isEqualByComparingTo("0");
      assertThat(calculation.getCurrentValueKrw()).isEqualByComparingTo("0");
      assertThat(calculation.getTotalAssetKrw()).isEqualByComparingTo("0");
      assertThat(calculation.getTotalReturnPercent()).isEqualByComparingTo("0");
      assertThat(calculation.getTotalReturnKrw()).isEqualByComparingTo("0");
      assertThat(calculation.getAveragePrice()).isNull();
      assertThat(calculation.getAverageFxRate()).isNull();
      assertThat(calculation.getCurrentFxRate()).isEqualByComparingTo("1300");
      assertThat(response.getDailyValues()).isEmpty();
      assertThat(response.getMetricsKrw()).isNull();
      verify(marketDataClient, never()).getDividendHistory(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("기간에 쓸 수 있는 시세가 하루도 없으면 DATA_NOT_FOUND")
    void noData() {
      ConditionalStrategyRequest request = perPurchase(3, 10).build();
      given(marketDataClient.getOHLCPriceRange(eq(SYMBOL), anyString(), anyString()))
        .willReturn(List.of(day(0, "0"), day(1, "0")));

      assertThatThrownBy(() -> strategy.execute(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.DATA_NOT_FOUND);
    }

    @Test
    @DisplayName("종료일이 오늘 이후면 오늘까지만 조회하고 현재가로 평가한다")
    void futureEndDateStopsToday() {
      LocalDate today = LocalDate.now();
      ConditionalStrategyRequest request = perPurchase(1, 10).startDate(today.minusDays(2)).endDate(today.plusDays(10)).build();
      OHLCPriceDto first = new OHLCPriceDto(SYMBOL, today.minusDays(2), BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN,
        BigDecimal.TEN, BigDecimal.TEN, 1_000L, "USD", true);
      BigDecimal eight = new BigDecimal("8");
      OHLCPriceDto second = new OHLCPriceDto(SYMBOL, today.minusDays(1), eight, eight, eight, eight, eight, 1_000L, "USD", true);
      given(marketDataClient.getOHLCPriceRange(SYMBOL, today.minusDays(2).toString(), today.toString()))
        .willReturn(List.of(first, second));
      given(marketDataClient.getBulkFxRates(any())).willReturn(Map.of());
      given(marketDataClient.getCurrentPrice(SYMBOL)).willReturn(new StockPriceDto(SYMBOL, null, new BigDecimal("12"),
        null, null, null, null, today, null, null, null, null, null, null, "USD", true, null, null));
      given(marketDataClient.getLatestFxRate()).willReturn(new FxRateDto(today, new BigDecimal("1300")));
      given(marketDataClient.getDividendHistory(eq(SYMBOL), anyString(), anyString())).willReturn(List.of());
      given(responseMapper.toStrategyResponse(any(ConditionalStrategyRequest.class), any(), any(), any(), any()))
        .willReturn(StrategyResponse.builder().build());

      strategy.execute(request);

      assertThat(executed()).hasSize(1);
      verify(marketDataClient).getOHLCPriceRange(SYMBOL, today.minusDays(2).toString(), today.toString());
    }

    @Nested
    @DisplayName("익절 · 손절")
    class TakeProfitStopLossTests {

      // 둘째 날 100 에 산 10주(첫날 종가 120 의 하락 기준가 108 아래 시가)
      private static final Tuple ENTRY = tuple("100", "16.7%하락", 1);

      // 회당 1000달러(환율 1300 고정) · 수수료 없음
      private ConditionalStrategyRequest.ConditionalStrategyRequestBuilder selling(int days, int maxPurchases) {
        return perPurchase(days, maxPurchases).purchaseFxRate(new BigDecimal("1300"));
      }

      // 첫날 · 둘째 날(10주 매수) 뒤에 이어지는 날
      private List<OHLCPriceDto> afterEntry(OHLCPriceDto... rest) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(day(0, "120"), bar(1, "100", "100", "100")),
          java.util.stream.Stream.of(rest)).toList();
      }

      private List<Tuple> trades() {
        return executed().stream()
          .map(tx -> tuple(tx.getPrice().stripTrailingZeros().toPlainString(), tx.getTrigger(), tx.getShares().signum()))
          .toList();
      }

      @Test
      @DisplayName("고가가 평균 단가 × (1 + 익절 비율) 에 닿으면 그 값에 전량 팔고 실현 손익을 남긴다")
      void sellsAtTakeProfitTrigger() {
        ConditionalStrategyRequest request = selling(3, 10).takeProfitRate(new BigDecimal("0.1")).build();
        stubMarket(request, afterEntry(candle(2, "101", "111", "100", "105")), false);

        strategy.execute(request);

        assertThat(trades()).containsExactly(ENTRY, tuple("110", "10.0%익절", -1));
        StrategyTransaction sale = executed().getLast();
        assertThat(sale.getShares()).isEqualByComparingTo("-10");
        assertThat(sale.getAmount()).isEqualByComparingTo("0");
        assertThat(sale.getProceeds()).isEqualByComparingTo("1100");
        assertThat(sale.getRealizedProfit()).isEqualByComparingTo("100");
      }

      @Test
      @DisplayName("시가부터 익절 기준가 위면 시가에 판다")
      void sellsAtOpenWhenGapAbove() {
        ConditionalStrategyRequest request = selling(3, 10).takeProfitRate(new BigDecimal("0.1")).build();
        stubMarket(request, afterEntry(candle(2, "115", "116", "112", "114")), false);

        strategy.execute(request);

        assertThat(trades()).containsExactly(ENTRY, tuple("115", "15.0%익절", -1));
      }

      @Test
      @DisplayName("저가가 손절 기준가에 닿으면 그 값에 팔고, 그 날짜에는 하락 매수 조건이 맞아도 사지 않는다")
      void stopLossBlocksSameDayBuy() {
        ConditionalStrategyRequest request = selling(3, 10).stopLossRate(new BigDecimal("0.1")).build();
        // 전날 종가 100 이라 하락 기준가도 90
        stubMarket(request, afterEntry(candle(2, "95", "96", "89", "92")), false);

        strategy.execute(request);

        assertThat(trades()).containsExactly(ENTRY, tuple("90", "10.0%손절", -1));
      }

      @Test
      @DisplayName("같은 날 익절 · 손절 기준가에 모두 닿고 시가가 둘 사이면 손절로 판다")
      void bothTouchedSellsAtStopLoss() {
        ConditionalStrategyRequest request = selling(3, 10).takeProfitRate(new BigDecimal("0.05"))
          .stopLossRate(new BigDecimal("0.05")).build();
        stubMarket(request, afterEntry(candle(2, "100", "106", "94", "100")), false);

        strategy.execute(request);

        assertThat(trades()).containsExactly(ENTRY, tuple("95", "5.0%손절", -1));
      }

      @Test
      @DisplayName("산 날은 고가가 익절 기준가 위여도 팔지 않는다")
      void noSellOnBuyDay() {
        ConditionalStrategyRequest request = selling(3, 10).takeProfitRate(new BigDecimal("0.05")).build();
        stubMarket(request, List.of(day(0, "120"), candle(1, "100", "120", "99", "100"), day(2, "100")), false);

        strategy.execute(request);

        assertThat(trades()).containsExactly(ENTRY);
      }

      @Test
      @DisplayName("판 뒤 다음 하락 매수는 판 돈을 먼저 쓰고 모자란 만큼만 새로 넣는다")
      void rebuyUsesCashFirst() {
        ConditionalStrategyRequest request = selling(4, 10).stopLossRate(new BigDecimal("0.1")).build();
        // 3일째 90 에 손절(900달러), 4일째 전날 종가 92 의 하락 기준가 82.8 에 1000달러어치
        stubMarket(request, afterEntry(candle(2, "95", "96", "89", "92"), bar(3, "83", "82", "85")), false);

        strategy.execute(request);

        assertThat(trades()).containsExactly(ENTRY, tuple("90", "10.0%손절", -1), tuple("82.8", "10.0%하락", 1));
        StrategyTransaction rebuy = executed().getLast();
        assertThat(rebuy.getCashUsed()).isEqualByComparingTo("900");
        // 모자란 100달러 × 1300
        assertThat(rebuy.getAmount()).isEqualByComparingTo("130000");
      }

      @Test
      @DisplayName("새 돈을 넣은 회차만 횟수에 세고, 켜면 마지막 매수 뒤에도 끝까지 보고 판 돈으로만 다시 산다")
      void runsToEndAfterMaxPurchases() {
        ConditionalStrategyRequest request = selling(6, 1).takeProfitRate(new BigDecimal("0.1")).build();
        // 4일째 110 에 익절(1100달러), 5일째 전날 종가 105 의 하락 기준가 94.5 아래 시가 94
        stubMarket(request, afterEntry(day(2, "100"), candle(3, "101", "111", "100", "105"), bar(4, "94", "94", "95"),
          day(5, "95")), false);

        strategy.execute(request);

        assertThat(trades()).containsExactly(ENTRY, tuple("110", "10.0%익절", -1), tuple("94", "10.5%하락", 1));
        StrategyTransaction rebuy = executed().getLast();
        assertThat(rebuy.getAmount()).isEqualByComparingTo("0");
        assertThat(rebuy.getCashUsed()).isEqualByComparingTo("1000");
      }

      @Test
      @DisplayName("예산을 다 쓴 뒤에도 판 돈이 회당 금액만큼 있으면 판 돈으로 산다")
      void rebuysWithCashAfterBudget() {
        ConditionalStrategyRequest request = selling(6, 10).maxPurchases(null).totalInvestment(new BigDecimal("1300000"))
          .takeProfitRate(new BigDecimal("0.1")).build();
        stubMarket(request, afterEntry(day(2, "100"), candle(3, "101", "111", "100", "105"), bar(4, "94", "94", "95"),
          day(5, "95")), false);

        StrategyResponse response = strategy.execute(request);

        assertThat(trades()).containsExactly(ENTRY, tuple("110", "10.0%익절", -1), tuple("94", "10.5%하락", 1));
        assertThat(executed().getLast().getAmount()).isEqualByComparingTo("0");
        assertThat(response.getSkippedSpan()).isNull();
      }

      @ParameterizedTest(name = "예산 {0}")
      @CsvSource({"1430000, 130000", "1300000, 0"})
      @DisplayName("판 돈이 모자라면 새로 넣을 돈이 남은 예산 안일 때만 채워 사고, 넘으면 판 돈만큼만 산다")
      void topUpWithinBudget(String budget, String deposit) {
        ConditionalStrategyRequest request = selling(4, 10).maxPurchases(null).totalInvestment(new BigDecimal(budget))
          .stopLossRate(new BigDecimal("0.1")).build();
        // 3일째 90 에 손절(900달러), 4일째 하락 기준가 82.8 에 모자란 100달러는 13만원
        stubMarket(request, afterEntry(candle(2, "95", "96", "89", "92"), bar(3, "83", "82", "85")), false);

        StrategyResponse response = strategy.execute(request);

        StrategyTransaction rebuy = executed().getLast();
        assertThat(rebuy.getCashUsed()).isEqualByComparingTo("900");
        assertThat(rebuy.getAmount()).isEqualByComparingTo(deposit);
        assertThat(response.getSkippedSpan()).isNull();
      }

      @Test
      @DisplayName("예산이 모자란 날짜 뒤로는 남은 예산이 있어도 새 돈을 채우지 않고 판 돈만큼만 산다")
      void noTopUpAfterShortfall() {
        ConditionalStrategyRequest request = selling(5, 10).maxPurchases(null).totalInvestment(new BigDecimal("2000000"))
          .stopLossRate(new BigDecimal("0.1")).build();
        // 3일째 99 매수는 남은 예산 70만원을 넘고, 4일째 90 에 손절(900달러), 5일째 79 에 모자란 100달러는 13만원
        stubMarket(request, List.of(day(0, "120"), bar(1, "100", "100", "110"), bar(2, "99", "95", "96"),
          candle(3, "95", "96", "85", "88"), bar(4, "79", "79", "80")), false);

        StrategyResponse response = strategy.execute(request);

        StrategyTransaction rebuy = executed().getLast();
        assertThat(rebuy.getPrice()).isEqualByComparingTo("79");
        assertThat(rebuy.getCashUsed()).isEqualByComparingTo("900");
        assertThat(rebuy.getAmount()).isEqualByComparingTo("0");
        assertThat(response.getSkippedSpan()).isEqualTo(new SkippedSpan(START.plusDays(2), 1, SkipReason.OVER_BUDGET));
      }

      @Test
      @DisplayName("주식 수로 사면 판 돈이 그 주식 수 값만큼 있을 때 판 돈으로만 산다")
      void sharesRebuyFromCash() {
        ConditionalStrategyRequest request = selling(4, 10).amountPerPurchase(null).sharesPerPurchase(10)
          .stopLossRate(new BigDecimal("0.1")).build();
        // 3일째 90 에 손절(900달러), 4일째 하락 기준가 82.8 에 10주는 828달러
        stubMarket(request, afterEntry(candle(2, "95", "96", "89", "92"), bar(3, "83", "82", "85")), false);

        strategy.execute(request);

        assertThat(trades()).containsExactly(ENTRY, tuple("90", "10.0%손절", -1), tuple("82.8", "10.0%하락", 1));
        StrategyTransaction rebuy = executed().getLast();
        assertThat(rebuy.getShares()).isEqualByComparingTo("10");
        assertThat(rebuy.getCashUsed()).isEqualByComparingTo("828");
        assertThat(rebuy.getAmount()).isEqualByComparingTo("0");
      }

      @Test
      @DisplayName("주식 수로 사면 최대 횟수를 다 쓴 뒤 판 돈이 없을 때 사지 않고 예산 부족으로도 세지 않는다")
      void sharesNoCashAfterMaxPurchases() {
        ConditionalStrategyRequest request = selling(3, 1).amountPerPurchase(null).sharesPerPurchase(10)
          .takeProfitRate(new BigDecimal("0.5")).build();
        // 3일째 전날 종가 100 의 하락 기준가 90 아래 89
        stubMarket(request, afterEntry(bar(2, "89", "89", "89")), false);

        StrategyResponse response = strategy.execute(request);

        assertThat(trades()).containsExactly(ENTRY);
        assertThat(response.getSkippedSpan()).isNull();
      }

      @Test
      @DisplayName("주식 수로 사면 최대 횟수를 다 쓴 뒤 판 돈이 모자랄 때 새 돈 없이 판 돈으로 살 수 있는 주식 수만 산다")
      void sharesCashOnlyAfterMaxPurchases() {
        ConditionalStrategyRequest request = selling(5, 1).amountPerPurchase(null).sharesPerPurchase(10)
          .stopLossRate(new BigDecimal("0.1")).build();
        // 3일째 90 에 손절(900달러), 5일째 전날 종가 120 의 하락 기준가 108 아래 시가 105 에 10주는 1,050달러
        stubMarket(request, afterEntry(candle(2, "95", "96", "89", "92"), day(3, "120"), bar(4, "105", "105", "106")),
          false);

        StrategyResponse response = strategy.execute(request);

        StrategyTransaction rebuy = executed().getLast();
        assertThat(rebuy.getPrice()).isEqualByComparingTo("105");
        assertThat(rebuy.getShares()).isEqualByComparingTo("8");
        assertThat(rebuy.getCashUsed()).isEqualByComparingTo("840");
        assertThat(rebuy.getAmount()).isEqualByComparingTo("0");
        assertThat(response.getSkippedSpan()).isNull();
      }

      @ParameterizedTest(name = "예산 {0} → {1}주")
      @CsvSource({"1495000, 10, 900, 195000", "1400000, 8, 840, 0"})
      @DisplayName("주식 수로 사면 판 돈이 모자랄 때 새로 넣을 돈이 남은 예산 안이면 채워 사고, 넘으면 판 돈으로 살 수 있는 주식 수만 산다")
      void sharesTopUpWithinBudget(String budget, int shares, String cashUsed, String deposit) {
        ConditionalStrategyRequest request = selling(5, 10).maxPurchases(null).amountPerPurchase(null).sharesPerPurchase(10)
          .totalInvestment(new BigDecimal(budget)).stopLossRate(new BigDecimal("0.1")).build();
        // 3일째 90 에 손절(900달러), 5일째 전날 종가 120 의 하락 기준가 108 아래 시가 105 에 10주는 1,050달러
        stubMarket(request, afterEntry(candle(2, "95", "96", "89", "92"), day(3, "120"), bar(4, "105", "105", "106")),
          false);

        StrategyResponse response = strategy.execute(request);

        StrategyTransaction rebuy = executed().getLast();
        assertThat(rebuy.getPrice()).isEqualByComparingTo("105");
        assertThat(rebuy.getShares()).isEqualByComparingTo(String.valueOf(shares));
        assertThat(rebuy.getCashUsed()).isEqualByComparingTo(cashUsed);
        assertThat(rebuy.getAmount()).isEqualByComparingTo(deposit);
        assertThat(response.getSkippedSpan()).isNull();
      }

      @Test
      @DisplayName("매도 수수료를 빼고, 실현 손익은 매수 수수료까지 든 원가를 뺀 값이다")
      void sellFee() {
        ConditionalStrategyRequest request = selling(3, 10).takeProfitRate(new BigDecimal("0.1"))
          .tradingFeeRate(new BigDecimal("0.01")).build();
        // 매수 수수료 10달러로 9.9주, 평균 단가 101.0101 → 기준가 111.1111
        stubMarket(request, afterEntry(candle(2, "105", "112", "104", "110")), false);

        strategy.execute(request);

        StrategyTransaction sale = executed().getLast();
        assertThat(sale.getFee()).isEqualByComparingTo("11.00");
        assertThat(sale.getProceeds()).isCloseTo(new BigDecimal("1089"), org.assertj.core.data.Offset.offset(new BigDecimal("0.01")));
        assertThat(sale.getRealizedProfit()).isEqualByComparingTo(sale.getProceeds().subtract(new BigDecimal("1000")));
      }

      @Test
      @DisplayName("매도일은 환율을 계산에 쓰지 않아 기본 환율로 계산한 날로 남기지 않는다")
      void saleIsNotDefaultFxDate() {
        ConditionalStrategyRequest request = perPurchase(3, 10).takeProfitRate(new BigDecimal("0.1")).build();
        // 일별 환율이 빈 응답이라 모든 날이 기본 환율
        stubMarket(request, afterEntry(candle(2, "101", "111", "100", "105")), true);

        strategy.execute(request);

        assertThat(executed()).extracting(StrategyTransaction::getDefaultFxDate).containsExactly(START.plusDays(1), null);
      }

      @Test
      @DisplayName("두 비율을 비우면 크게 올라도 팔지 않고 이전과 같이 산다")
      void noRatesNoSell() {
        ConditionalStrategyRequest request = selling(3, 10).build();
        stubMarket(request, afterEntry(candle(2, "101", "130", "100", "125")), false);

        strategy.execute(request);

        assertThat(executed()).singleElement().satisfies(tx -> {
          assertThat(tx.getCashUsed()).isNull();
          assertThat(tx.getProceeds()).isNull();
        });
      }

      @Test
      @DisplayName("배당 재투자와 함께 켜면 막는다")
      void rejectsWithReinvest() {
        ConditionalStrategyRequest request = selling(2, 10).takeProfitRate(new BigDecimal("0.1"))
          .reinvestDividends(true).build();

        assertThatThrownBy(() -> strategy.execute(request))
          .isInstanceOf(BacktestException.class).extracting("code").isEqualTo(BacktestResponse.SELL_WITH_REINVEST);
        verifyNoInteractions(marketDataClient);
      }

      @Test
      @DisplayName("익절 비율이 0 이하이거나 손절 비율이 1 이상이면 막는다")
      void rejectsBadRates() {
        assertThatThrownBy(() -> strategy.execute(selling(2, 10).takeProfitRate(BigDecimal.ZERO).build()))
          .isInstanceOf(BacktestException.class).extracting("code").isEqualTo(BacktestResponse.INVALID_SELL_RATE);
        assertThatThrownBy(() -> strategy.execute(selling(2, 10).stopLossRate(BigDecimal.ONE).build()))
          .isInstanceOf(BacktestException.class).extracting("code").isEqualTo(BacktestResponse.INVALID_SELL_RATE);
      }
    }
  }

  @Nested
  @DisplayName("요청 타입 검사")
  class RequestTypeTests {

    @Test
    @DisplayName("적립식 요청이 오면 전략 타입 불일치로 막는다")
    void execute_OtherRequestType_Rejected() {
      DcaStrategyRequest other = DcaStrategyRequest.builder().symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1)).endDate(LocalDate.of(2024, 1, 1)).build();

      assertThatThrownBy(() -> strategy.execute(other))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.STRATEGY_TYPE_MISMATCH);
      verifyNoInteractions(marketDataClient);
    }
  }
}
