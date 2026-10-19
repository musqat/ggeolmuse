package com.muscat.backtest.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.never;

import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.enums.type.InvestmentMode;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.MarketDataClient;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import com.muscat.commonlib.dto.StockPriceDto;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConditionalPurchaseStrategy 테스트")
class ConditionalPurchaseStrategyTest {

  @Mock
  private MarketDataClient marketDataClient;

  @Mock
  private ResponseMapper responseMapper;

  private ConditionalPurchaseStrategy strategy;

  @BeforeEach
  void setUp() {
    strategy = new ConditionalPurchaseStrategy(marketDataClient, responseMapper);
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
  @DisplayName("조정종가 트리거 테스트")
  class AdjustedCloseTriggerTests {

    @Test
    @DisplayName("분할일을 폭락으로 오인해 유령매수하지 않는다 - closePrice 가 분할 조정된 값이라")
    void execute_SplitDay_NoPhantomDropBuy() {
      // given: PER_PURCHASE, 20% 하락시 매수, 최대 5회
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .userId("test-user")
        .symbol("SPLIT-TEST")
        .startDate(LocalDate.of(2024, 1, 15))
        .endDate(LocalDate.of(2024, 1, 16))
        .investmentMode(InvestmentMode.PER_PURCHASE)
        .amountPerPurchase(new BigDecimal("1000.00"))
        .maxPurchases(5)
        .dropPercentage(new BigDecimal("0.2")) // 20%
        .build();

      // 수집 단계에서 분할이 close 에 반영돼 온다. 분할 전후로 25 로 평탄하다
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
      given(marketDataClient.getDividendHistory(eq("SPLIT-TEST"), anyString(), anyString()))
        .willReturn(java.util.Collections.emptyList());
      given(responseMapper.toStrategyResponse(any(ConditionalStrategyRequest.class), any(), any(), any()))
        .willReturn(StrategyResponse.builder().strategyType(StrategyType.CONDITIONAL_PURCHASE).build());

      // when
      strategy.execute(request);

      // then: 거래는 초기매수 1건뿐(분할일 유령매수 없음), 매수가는 조정종가 25
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<StrategyTransaction>> captor = ArgumentCaptor.forClass(List.class);
      verify(responseMapper).toStrategyResponse(
        any(ConditionalStrategyRequest.class), captor.capture(), any(), any());
      List<StrategyTransaction> txs = captor.getValue();
      assertThat(txs).hasSize(1);
      assertThat(txs.getFirst().getPrice()).isEqualByComparingTo("25.00");
    }
  }

  @Nested
  @DisplayName("수수료")
  class FeeTests {

    @Test
    @DisplayName("매수마다 수수료를 떼고 남은 달러로 산다")
    void execute_DeductsFee() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .userId("test-user")
        .symbol("FEE-TEST")
        .startDate(LocalDate.of(2024, 1, 15))
        .endDate(LocalDate.of(2024, 1, 16))
        .investmentMode(InvestmentMode.PER_PURCHASE)
        .amountPerPurchase(new BigDecimal("1300000"))
        .maxPurchases(1)
        .dropPercentage(new BigDecimal("0.2"))
        .tradingFeeRate(new BigDecimal("0.01"))
        .build();

      OHLCPriceDto day1 = new OHLCPriceDto("FEE-TEST", LocalDate.of(2024, 1, 15),
        new BigDecimal("99"), new BigDecimal("101"), new BigDecimal("98"),
        new BigDecimal("100.00"), new BigDecimal("100.00"), 1_000_000L, "USD", true);
      given(marketDataClient.getOHLCPriceRange(eq("FEE-TEST"), eq("2024-01-15"), eq("2024-01-16")))
        .willReturn(List.of(day1));
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
      given(responseMapper.toStrategyResponse(any(ConditionalStrategyRequest.class), any(), any(), any()))
        .willReturn(StrategyResponse.builder().build());

      strategy.execute(request);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<StrategyTransaction>> captor = ArgumentCaptor.forClass(List.class);
      verify(responseMapper).toStrategyResponse(
        any(ConditionalStrategyRequest.class), captor.capture(), any(), any());
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
        .totalInvestment(BigDecimal.valueOf(10000))
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
        .totalInvestment(BigDecimal.valueOf(10000))
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
        .totalInvestment(BigDecimal.valueOf(10000))
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
        .totalInvestment(BigDecimal.valueOf(10000))
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
        .totalInvestment(BigDecimal.valueOf(10000))
        .dropPercentage(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.execute(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.STRATEGY_DATE_RANGE_INVALID);
    }

    @Test
    @DisplayName("TOTAL_BUDGET 모드에서 totalInvestment가 null이면 예외 발생")
    void validateRequest_TotalBudgetMode_NullTotalInvestment_ThrowsException() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .investmentMode(InvestmentMode.TOTAL_BUDGET)
        .totalInvestment(null)
        .dropPercentage(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.execute(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.STRATEGY_TOTAL_INVESTMENT_REQUIRED);
    }

    @Test
    @DisplayName("TOTAL_BUDGET 모드에서 totalInvestment가 0 이하면 예외 발생")
    void validateRequest_TotalBudgetMode_ZeroTotalInvestment_ThrowsException() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .investmentMode(InvestmentMode.TOTAL_BUDGET)
        .totalInvestment(BigDecimal.ZERO)
        .dropPercentage(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.execute(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.STRATEGY_TOTAL_INVESTMENT_REQUIRED);
    }

    @Test
    @DisplayName("PER_PURCHASE 모드에서 amountPerPurchase가 null이면 예외 발생")
    void validateRequest_PerPurchaseMode_NullAmountPerPurchase_ThrowsException() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .investmentMode(InvestmentMode.PER_PURCHASE)
        .amountPerPurchase(null)
        .maxPurchases(5)
        .dropPercentage(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.execute(request))
        .isInstanceOf(BacktestException.class)
        .hasMessageContaining("회당 투자금액은 필수입니다");
    }

    @Test
    @DisplayName("PER_PURCHASE 모드에서 maxPurchases가 null이면 예외 발생")
    void validateRequest_PerPurchaseMode_NullMaxPurchases_ThrowsException() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .investmentMode(InvestmentMode.PER_PURCHASE)
        .amountPerPurchase(BigDecimal.valueOf(1000))
        .maxPurchases(null)
        .dropPercentage(BigDecimal.valueOf(10))
        .build();

      assertThatThrownBy(() -> strategy.execute(request))
        .isInstanceOf(BacktestException.class)
        .hasMessageContaining("최대 매수 횟수가 필수입니다");
    }

    @Test
    @DisplayName("dropPercentage가 null이면 예외 발생")
    void validateRequest_NullDropPercentage_ThrowsException() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .totalInvestment(BigDecimal.valueOf(10000))
        .dropPercentage(null)
        .build();

      assertThatThrownBy(() -> strategy.execute(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.STRATEGY_DROP_PERCENTAGE_REQUIRED);
    }

    @Test
    @DisplayName("dropPercentage가 0 이하면 예외 발생")
    void validateRequest_ZeroDropPercentage_ThrowsException() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .totalInvestment(BigDecimal.valueOf(10000))
        .dropPercentage(BigDecimal.ZERO)
        .build();

      assertThatThrownBy(() -> strategy.execute(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.STRATEGY_DROP_PERCENTAGE_REQUIRED);
    }

    @Test
    @DisplayName("maxPurchases가 0 이하면 예외 발생")
    void validateRequest_ZeroMaxPurchases_ThrowsException() {
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2023, 12, 31))
        .totalInvestment(BigDecimal.valueOf(10000))
        .dropPercentage(BigDecimal.valueOf(10))
        .maxPurchases(0)
        .build();

      assertThatThrownBy(() -> strategy.execute(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.INVALID_MAX_PURCHASES);
    }
  }

  @Nested
  @DisplayName("하락 판정 · 투자 모드")
  class DropTriggerTests {

    private static final String SYMBOL = "DROP";
    private static final LocalDate START = LocalDate.of(2024, 3, 4);

    private OHLCPriceDto day(int offset, String close) {
      BigDecimal price = new BigDecimal(close);
      return new OHLCPriceDto(SYMBOL, START.plusDays(offset), price, price, price, price, price, 1_000L, "USD", true);
    }

    // 회당 130만원(환율 1300 이면 1000달러), 직전 매수가 대비 10% 하락 시 매수
    private ConditionalStrategyRequest.ConditionalStrategyRequestBuilder perPurchase(int days, int maxPurchases) {
      return ConditionalStrategyRequest.builder().userId("test-user").symbol(SYMBOL)
        .startDate(START).endDate(START.plusDays(days - 1))
        .investmentMode(InvestmentMode.PER_PURCHASE).amountPerPurchase(new BigDecimal("1300000"))
        .maxPurchases(maxPurchases).dropPercentage(new BigDecimal("0.1"));
    }

    // 기간 시세 · 평가일(과거) 시세와 환율 · 배당. 일별 환율은 빈 응답이라 기본 환율로 채워진다
    private void stubMarket(ConditionalStrategyRequest request, List<OHLCPriceDto> prices, boolean dailyFx) {
      given(marketDataClient.getOHLCPriceRange(SYMBOL, request.getStartDate().toString(), request.getEndDate().toString()))
        .willReturn(prices);
      if (dailyFx) {
        given(marketDataClient.getBulkFxRates(any())).willReturn(Map.of());
      }
      given(marketDataClient.getOHLCPrice(eq(SYMBOL), anyString())).willReturn(prices.getLast());
      given(marketDataClient.getFxRate(anyString())).willReturn(new FxRateDto(request.getEndDate(), new BigDecimal("1300")));
      given(marketDataClient.getDividendHistory(eq(SYMBOL), anyString(), anyString())).willReturn(List.of());
      given(responseMapper.toStrategyResponse(any(ConditionalStrategyRequest.class), any(), any(), any()))
        .willReturn(StrategyResponse.builder().build());
    }

    private List<StrategyTransaction> executed() {
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<StrategyTransaction>> captor = ArgumentCaptor.forClass(List.class);
      verify(responseMapper).toStrategyResponse(any(ConditionalStrategyRequest.class), captor.capture(), any(), any());
      return captor.getValue();
    }

    @Test
    @DisplayName("직전 매수가보다 기준 이상 떨어진 날만 산다. 사지 않은 날의 가격은 기준이 되지 않는다")
    void buysOnDropFromLastPurchase() {
      ConditionalStrategyRequest request = perPurchase(5, 10).build();
      // 100 매수 → 95(-5%) 안 삼 → 89(100 대비 -11%) 매수 → 85(89 대비 -4.5%) 안 삼 → 80(89 대비 -10.1%) 매수
      stubMarket(request, List.of(day(0, "100"), day(1, "95"), day(2, "89"), day(3, "85"), day(4, "80")), true);

      strategy.execute(request);

      assertThat(executed()).extracting(StrategyTransaction::getPrice, StrategyTransaction::getTrigger)
        .containsExactly(
          tuple(new BigDecimal("100"), "초기매수"),
          tuple(new BigDecimal("89"), "11.0%하락"),
          tuple(new BigDecimal("80"), "10.1%하락"));
    }

    @Test
    @DisplayName("오른 날은 기준 이상 올라도 사지 않는다")
    void noBuyOnRise() {
      ConditionalStrategyRequest request = perPurchase(3, 10).build();
      stubMarket(request, List.of(day(0, "100"), day(1, "120"), day(2, "130")), true);

      strategy.execute(request);

      assertThat(executed()).singleElement().extracting(StrategyTransaction::getTrigger).isEqualTo("초기매수");
    }

    @Test
    @DisplayName("총 예산 모드에 회당 금액을 주면 예산 ÷ 회당 금액 번까지만 산다")
    void totalBudgetWithAmountPerPurchase() {
      ConditionalStrategyRequest request = perPurchase(4, 10).investmentMode(InvestmentMode.TOTAL_BUDGET)
        .maxPurchases(null).totalInvestment(new BigDecimal("3900000")).build();
      stubMarket(request, List.of(day(0, "100"), day(1, "80"), day(2, "64"), day(3, "50")), true);

      strategy.execute(request);

      assertThat(executed()).hasSize(3).allSatisfy(tx -> assertThat(tx.getAmount()).isEqualByComparingTo("1300000"));
    }

    @Test
    @DisplayName("모드를 비우면 총 예산 모드이고, 회당 금액도 횟수도 없으면 10회로 나눈다")
    void defaultModeSplitsIntoTen() {
      ConditionalStrategyRequest request = perPurchase(1, 10).investmentMode(null).amountPerPurchase(null)
        .maxPurchases(null).totalInvestment(new BigDecimal("1000000")).build();
      stubMarket(request, List.of(day(0, "100")), true);

      strategy.execute(request);

      assertThat(executed()).singleElement().extracting(StrategyTransaction::getAmount)
        .satisfies(amount -> assertThat(amount).isEqualByComparingTo("100000"));
    }

    @Test
    @DisplayName("매수 환율을 직접 주면 일별 환율을 조회하지 않고 그 값으로 산다")
    void manualPurchaseFxRate() {
      ConditionalStrategyRequest request = perPurchase(1, 10).purchaseFxRate(new BigDecimal("1200")).build();
      stubMarket(request, List.of(day(0, "100")), false);

      strategy.execute(request);

      assertThat(executed()).singleElement().extracting(StrategyTransaction::getFxRate)
        .satisfies(rate -> assertThat(rate).isEqualByComparingTo("1200"));
      verify(marketDataClient, never()).getBulkFxRates(any());
    }

    @Test
    @DisplayName("계산에 실패한 날은 건너뛰고 다음 날 첫 매수를 한다")
    void skipsFailedDay() {
      ConditionalStrategyRequest request = perPurchase(2, 10).build();
      // 가격 0 은 주식 수 계산에서 예외가 난다
      stubMarket(request, List.of(day(0, "0"), day(1, "100")), true);

      strategy.execute(request);

      assertThat(executed()).singleElement().satisfies(tx -> {
        assertThat(tx.getDate()).isEqualTo(START.plusDays(1));
        assertThat(tx.getTrigger()).isEqualTo("초기매수");
      });
    }

    @Test
    @DisplayName("살 수 있는 날이 하루도 없으면 DATA_NOT_FOUND")
    void noData() {
      ConditionalStrategyRequest request = perPurchase(3, 10).build();
      given(marketDataClient.getOHLCPriceRange(eq(SYMBOL), anyString(), anyString())).willReturn(List.of());
      given(marketDataClient.getBulkFxRates(any())).willReturn(Map.of());

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
      given(marketDataClient.getOHLCPriceRange(SYMBOL, today.minusDays(2).toString(), today.toString()))
        .willReturn(List.of(first));
      given(marketDataClient.getBulkFxRates(any())).willReturn(Map.of());
      given(marketDataClient.getCurrentPrice(SYMBOL)).willReturn(new StockPriceDto(SYMBOL, null, new BigDecimal("12"),
        null, null, null, null, today, null, null, null, null, null, null, "USD", true, null, null));
      given(marketDataClient.getLatestFxRate()).willReturn(new FxRateDto(today, new BigDecimal("1300")));
      given(marketDataClient.getDividendHistory(eq(SYMBOL), anyString(), anyString())).willReturn(List.of());
      given(responseMapper.toStrategyResponse(any(ConditionalStrategyRequest.class), any(), any(), any()))
        .willReturn(StrategyResponse.builder().build());

      strategy.execute(request);

      assertThat(executed()).hasSize(1);
      verify(marketDataClient).getOHLCPriceRange(SYMBOL, today.minusDays(2).toString(), today.toString());
    }

    @Test
    @DisplayName("회당 고정 모드에서 회당 금액이 0 이거나 최대 횟수가 0 이면 막는다")
    void perPurchaseZeroValues() {
      ConditionalStrategyRequest zeroAmount = perPurchase(1, 3).amountPerPurchase(BigDecimal.ZERO).build();
      ConditionalStrategyRequest zeroCount = perPurchase(1, 0).build();

      assertThatThrownBy(() -> strategy.execute(zeroAmount))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.STRATEGY_TOTAL_INVESTMENT_REQUIRED);
      assertThatThrownBy(() -> strategy.execute(zeroCount))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.INVALID_MAX_PURCHASES);
    }

    @Test
    @DisplayName("총 예산 모드에 최대 횟수만 주면 예산을 그 횟수로 나눈다")
    void totalBudgetSplitsByMaxPurchases() {
      ConditionalStrategyRequest request = perPurchase(1, 4).investmentMode(InvestmentMode.TOTAL_BUDGET)
        .amountPerPurchase(null).totalInvestment(new BigDecimal("1000000")).build();
      stubMarket(request, List.of(day(0, "100")), true);

      strategy.execute(request);

      assertThat(executed()).singleElement().extracting(StrategyTransaction::getAmount)
        .satisfies(amount -> assertThat(amount).isEqualByComparingTo("250000"));
    }

    @Test
    @DisplayName("총 예산 모드에서 회당 금액 0 은 없는 것으로 보고 10회로 나눈다")
    void totalBudgetZeroAmountPerPurchase() {
      ConditionalStrategyRequest request = perPurchase(1, 10).investmentMode(InvestmentMode.TOTAL_BUDGET)
        .amountPerPurchase(BigDecimal.ZERO).maxPurchases(null).totalInvestment(new BigDecimal("1000000")).build();
      stubMarket(request, List.of(day(0, "100")), true);

      strategy.execute(request);

      assertThat(executed()).singleElement().extracting(StrategyTransaction::getAmount)
        .satisfies(amount -> assertThat(amount).isEqualByComparingTo("100000"));
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
