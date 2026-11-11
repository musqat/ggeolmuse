package com.muscat.backtest.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.muscat.backtest.common.calculation.StrategyCalculationResult;
import com.muscat.backtest.common.enums.type.DcaFrequency;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.enums.type.SkipReason;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.response.CapitalGainsTax;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.SkippedRound;
import com.muscat.backtest.domain.model.SkippedSpan;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import com.muscat.backtest.infra.client.dto.DividendDto;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.dto.StockPriceDto;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.enums.response.BacktestResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("DCA 전략 단위 테스트")
class DCAStrategyTest {

  @Mock
  private MarketDataClientWrapper marketDataClient;

  @Mock
  private ResponseMapper responseMapper;

  @InjectMocks
  private DCAStrategy dcaStrategy;

  private String userId;
  private String symbol;
  private LocalDate startDate;
  private LocalDate endDate;

  @BeforeEach
  void setUp() {
    userId = "test-user";
    symbol = "DCA-TEST";
    startDate = LocalDate.of(2024, 1, 1);
    endDate = LocalDate.of(2024, 12, 31);
    lenient().when(marketDataClient.defaultFxRate()).thenReturn(new BigDecimal("1350"));
    // 응답 매퍼는 빈 응답(일별 가치 · 지표를 채우는 자리)
    lenient().when(responseMapper.toStrategyResponse(any(DcaStrategyRequest.class), any(), any(StrategyCalculationResult.class),
      any(), any()))
      .thenReturn(StrategyResponse.builder().build());
  }

  @Nested
  @DisplayName("일별 평가 가치 · 지표")
  class PerformanceTests {

    @Test
    @DisplayName("결과에 일별 평가 가치와 입금을 뺀 지표를 싣는다")
    void fillsDailyValuesAndMetrics() {
      // 매달 15일 130만원, 2월 1일에 90 으로 내렸다가 2월 15일 105 · 3월 15일 110
      DcaStrategyRequest request = DcaStrategyRequest.builder().userId(userId).symbol(symbol)
        .startDate(startDate).endDate(LocalDate.of(2024, 3, 31))
        .amountPerPurchase(new BigDecimal("1300000")).purchaseDay(15).build();
      given(marketDataClient.getOHLCPriceRange(eq(symbol), eq("2024-01-01"), eq("2024-03-31"))).willReturn(List.of(
        createOHLC(LocalDate.of(2024, 1, 15), new BigDecimal("100.00")),
        createOHLC(LocalDate.of(2024, 2, 1), new BigDecimal("90.00")),
        createOHLC(LocalDate.of(2024, 2, 15), new BigDecimal("105.00")),
        createOHLC(LocalDate.of(2024, 3, 15), new BigDecimal("110.00"))));
      given(marketDataClient.getBulkFxRates(any())).willReturn(java.util.Map.of(
        "2024-01-15", new BigDecimal("1300"), "2024-02-01", new BigDecimal("1300"),
        "2024-02-15", new BigDecimal("1300"), "2024-03-15", new BigDecimal("1300")));
      given(marketDataClient.getFxRate(anyString())).willReturn(new FxRateDto(LocalDate.of(2024, 3, 31), new BigDecimal("1300")));
      given(marketDataClient.getOHLCPrice(eq(symbol), anyString()))
        .willReturn(createOHLC(LocalDate.of(2024, 3, 29), new BigDecimal("110.00")));
      given(marketDataClient.getDividendHistory(eq(symbol), anyString(), anyString())).willReturn(java.util.Collections.emptyList());
      given(responseMapper.toStrategyResponse(any(DcaStrategyRequest.class), any(), any(StrategyCalculationResult.class), any(), any()))
        .willReturn(StrategyResponse.builder().strategyType(StrategyType.DCA).build());

      StrategyResponse result = dcaStrategy.execute(request);

      assertThat(result.getDailyValues()).extracting(com.muscat.backtest.domain.dto.response.DailyValue::date)
        .containsExactly(LocalDate.of(2024, 1, 15), LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 15),
          LocalDate.of(2024, 3, 15), LocalDate.of(2024, 3, 31));
      // 입금을 빼면 2월 1일 -10% 가 가장 깊은 하락, 2월 15일에 회복
      assertThat(result.getMetricsKrw().mdd()).isNegative();
      assertThat(result.getMetricsKrw().mddTroughDate()).isEqualTo(LocalDate.of(2024, 2, 1));
      assertThat(result.getMetricsKrw().mddRecoveryDate()).isEqualTo(LocalDate.of(2024, 2, 15));
      assertThat(result.getMetricsUsd()).isNotNull();
      verify(marketDataClient, times(1)).getBulkFxRates(any());
    }
  }

  @Nested
  @DisplayName("DCA 전략 실행 테스트")
  class ExecuteDcaTests {

    @Test
    @DisplayName("매월 정기 매수가 실행된다")
    void execute_MonthlyPurchase_Success() {
      // given
      BigDecimal amountPerPurchase = new BigDecimal("1000.00");
      Integer purchaseDay = 15;

      DcaStrategyRequest request = DcaStrategyRequest.builder()
        .userId(userId)
        .symbol(symbol)
        .startDate(startDate)
        .endDate(LocalDate.of(2024, 3, 31)) // 3개월
        .amountPerPurchase(amountPerPurchase)
        .purchaseDay(purchaseDay)
        .build();

      // BULK API: getOHLCPriceRange() Mock
      List<OHLCPriceDto> ohlcPrices = List.of(
        createOHLC(LocalDate.of(2024, 1, 15), new BigDecimal("100.00")),
        createOHLC(LocalDate.of(2024, 2, 15), new BigDecimal("105.00")),
        createOHLC(LocalDate.of(2024, 3, 15), new BigDecimal("110.00"))
      );
      given(marketDataClient.getOHLCPriceRange(eq(symbol), eq("2024-01-01"), eq("2024-03-31")))
        .willReturn(ohlcPrices);

      // BULK API: getBulkFxRates() Mock
      java.util.Map<String, BigDecimal> fxRates = java.util.Map.of(
        "2024-01-15", new BigDecimal("1300.00"),
        "2024-02-15", new BigDecimal("1300.00"),
        "2024-03-15", new BigDecimal("1300.00")
      );
      given(marketDataClient.getBulkFxRates(any())).willReturn(fxRates);

      // 환율 데이터 Mock
      FxRateDto fxRate = new FxRateDto(LocalDate.now(),
        new BigDecimal("1300.00"));
      given(marketDataClient.getFxRate(anyString())).willReturn(fxRate);

      // 평가일 시세 Mock
      given(marketDataClient.getOHLCPrice(eq(symbol), anyString()))
        .willReturn(createOHLC(request.getEndDate(), new BigDecimal("110.00")));

      // 배당 데이터 Mock (빈 리스트)
      given(marketDataClient.getDividendHistory(eq(symbol), anyString(), anyString()))
        .willReturn(java.util.Collections.emptyList());

      // ResponseMapper Mock
      StrategyResponse expectedResponse = StrategyResponse.builder()
        .strategyType(StrategyType.DCA)
        .totalInvested(new BigDecimal("3000.00"))
        .totalShares(new BigDecimal("28.96"))
        .averagePrice(new BigDecimal("103.59"))
        .currentValue(new BigDecimal("3185.60"))
        .totalReturnPercent(new BigDecimal("6.19"))
        .transactions(createDcaTransactions())
        .build();

      given(responseMapper.toStrategyResponse(any(DcaStrategyRequest.class),
        any(), any(StrategyCalculationResult.class), any(), any()))
        .willReturn(expectedResponse);

      // when
      StrategyResponse result = dcaStrategy.execute(request);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getStrategyType()).isEqualTo(StrategyType.DCA);
      assertThat(result.getTotalInvested()).isEqualByComparingTo(new BigDecimal("3000.00"));
      assertThat(result.getTotalReturnPercent()).isGreaterThan(BigDecimal.ZERO);

      // Bulk API 호출 검증
      verify(marketDataClient, times(1)).getOHLCPriceRange(eq(symbol), anyString(), anyString());
      verify(marketDataClient, times(1)).getBulkFxRates(any());
    }

    @Test
    @DisplayName("투자일이 휴장일이면 다음 거래일 종가 · 환율로 산다")
    void execute_HolidayInvestmentDay_BuysOnNextTradingDay() {
      // given: 6/1 은 토요일, 그 전 금요일(5/31) 시세도 오지만 다음 거래일 6/3 에 삼
      DcaStrategyRequest request = DcaStrategyRequest.builder()
        .userId(userId)
        .symbol(symbol)
        .startDate(LocalDate.of(2024, 6, 1))
        .endDate(LocalDate.of(2024, 6, 30))
        .amountPerPurchase(new BigDecimal("1380000"))
        .purchaseDay(1)
        .build();

      given(marketDataClient.getOHLCPriceRange(eq(symbol), eq("2024-06-01"), eq("2024-06-30")))
        .willReturn(List.of(
          createOHLC(LocalDate.of(2024, 5, 31), new BigDecimal("100.00")),
          createOHLC(LocalDate.of(2024, 6, 3), new BigDecimal("125.00"))));
      given(marketDataClient.getBulkFxRates(any())).willReturn(java.util.Map.of(
        "2024-06-01", new BigDecimal("1300"),
        "2024-06-03", new BigDecimal("1380")));
      given(marketDataClient.getFxRate(anyString()))
        .willReturn(new FxRateDto(LocalDate.now(), new BigDecimal("1380")));
      given(marketDataClient.getOHLCPrice(eq(symbol), anyString()))
        .willReturn(createOHLC(request.getEndDate(), new BigDecimal("125.00")));
      given(marketDataClient.getDividendHistory(eq(symbol), anyString(), anyString()))
        .willReturn(java.util.Collections.emptyList());

      // when
      dcaStrategy.execute(request);

      // then: 138만원 ÷ 1380 = 1000달러 → 125달러에 8주
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<StrategyTransaction>> transactions = ArgumentCaptor.forClass(List.class);
      verify(responseMapper).toStrategyResponse(any(DcaStrategyRequest.class), transactions.capture(),
        any(StrategyCalculationResult.class), any(), any());
      StrategyTransaction first = transactions.getValue().get(0);
      assertThat(first.getDate()).isEqualTo(LocalDate.of(2024, 6, 1));
      assertThat(first.getActualDate()).isEqualTo(LocalDate.of(2024, 6, 3));
      assertThat(first.getPrice()).isEqualByComparingTo("125.00");
      assertThat(first.getFxRate()).isEqualByComparingTo("1380");
      assertThat(first.getShares()).isEqualByComparingTo("8");
      assertThat(first.getDefaultFxDate()).isNull();
    }

    @Test
    @DisplayName("실제 산 날 환율이 없으면 기본 환율로 사고 그 날을 거래에 표시한다")
    void execute_MissingFx_MarksTransaction() {
      DcaStrategyRequest request = DcaStrategyRequest.builder()
        .userId(userId)
        .symbol(symbol)
        .startDate(LocalDate.of(2024, 6, 1))
        .endDate(LocalDate.of(2024, 6, 30))
        .amountPerPurchase(new BigDecimal("1350000"))
        .purchaseDay(1)
        .build();
      given(marketDataClient.getOHLCPriceRange(eq(symbol), eq("2024-06-01"), eq("2024-06-30")))
        .willReturn(List.of(createOHLC(LocalDate.of(2024, 6, 3), new BigDecimal("125.00"))));
      given(marketDataClient.getBulkFxRates(any())).willReturn(java.util.Map.of());
      given(marketDataClient.getFxRate(anyString()))
        .willReturn(new FxRateDto(LocalDate.now(), new BigDecimal("1380")));
      given(marketDataClient.getOHLCPrice(eq(symbol), anyString()))
        .willReturn(createOHLC(request.getEndDate(), new BigDecimal("125.00")));
      given(marketDataClient.getDividendHistory(eq(symbol), anyString(), anyString()))
        .willReturn(java.util.Collections.emptyList());

      dcaStrategy.execute(request);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<StrategyTransaction>> transactions = ArgumentCaptor.forClass(List.class);
      verify(responseMapper).toStrategyResponse(any(DcaStrategyRequest.class), transactions.capture(),
        any(StrategyCalculationResult.class), any(), any());
      StrategyTransaction first = transactions.getValue().get(0);
      assertThat(first.getFxRate()).isEqualByComparingTo("1350");
      assertThat(first.getDefaultFxDate()).isEqualTo(LocalDate.of(2024, 6, 3));
    }

    @Test
    @DisplayName("매수가는 분할만 조정된 closePrice 를 쓴다. 배당 조정된 adjustedClose 가 아니다")
    void execute_UsesClosePrice_NotAdjustedClose() {
      // given: 분할 조정된 종가 100, 배당까지 조정된 값 25
      DcaStrategyRequest request = DcaStrategyRequest.builder()
        .userId(userId)
        .symbol(symbol)
        .startDate(LocalDate.of(2024, 1, 1))
        .endDate(LocalDate.of(2024, 1, 31))
        .amountPerPurchase(new BigDecimal("1000.00"))
        .purchaseDay(15)
        .build();

      OHLCPriceDto splitDay = new OHLCPriceDto(symbol, LocalDate.of(2024, 1, 15),
        new BigDecimal("99"), new BigDecimal("101"), new BigDecimal("98"),
        new BigDecimal("100.00"),  // closePrice (분할 조정)
        new BigDecimal("25.00"),   // adjustedClose (배당까지 조정)
        1000000L, "USD", true);
      given(marketDataClient.getOHLCPriceRange(eq(symbol), eq("2024-01-01"), eq("2024-01-31")))
        .willReturn(List.of(splitDay));
      given(marketDataClient.getBulkFxRates(any()))
        .willReturn(java.util.Map.of("2024-01-15", new BigDecimal("1300.00")));
      given(marketDataClient.getFxRate(anyString()))
        .willReturn(new FxRateDto(LocalDate.now(), new BigDecimal("1300.00")));
      given(marketDataClient.getOHLCPrice(eq(symbol), anyString()))
        .willReturn(createOHLC(request.getEndDate(), new BigDecimal("30.00")));
      given(marketDataClient.getDividendHistory(eq(symbol), anyString(), anyString()))
        .willReturn(java.util.Collections.emptyList());
      given(responseMapper.toStrategyResponse(any(DcaStrategyRequest.class), any(), any(), any(), any()))
        .willReturn(StrategyResponse.builder().strategyType(StrategyType.DCA).build());

      // when
      dcaStrategy.execute(request);

      // then: 매수가가 closePrice(100) 여야 함 (adjustedClose 25 아님)
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<StrategyTransaction>> captor = ArgumentCaptor.forClass(List.class);
      verify(responseMapper).toStrategyResponse(any(DcaStrategyRequest.class), captor.capture(), any(), any(), any());
      List<StrategyTransaction> txs = captor.getValue();
      assertThat(txs).hasSize(1);
      assertThat(txs.getFirst().getPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("가격 데이터가 없으면 예외가 발생한다")
    void execute_NoPriceData_ThrowsException() {
      // given
      DcaStrategyRequest request = DcaStrategyRequest.builder()
        .userId(userId)
        .symbol("INVALID")
        .startDate(startDate)
        .endDate(endDate)
        .amountPerPurchase(new BigDecimal("1000.00"))
        .purchaseDay(15)
        .build();

      // 기간 시세 없음
      given(marketDataClient.getOHLCPriceRange(eq("INVALID"), anyString(), anyString()))
        .willReturn(java.util.Collections.emptyList());

      // when & then
      assertThatThrownBy(() -> dcaStrategy.execute(request))
        .isInstanceOf(com.muscat.backtest.common.exception.BacktestException.class)
        .hasMessageContaining("적립식 투자 전략 실행 가능한 데이터가 없습니다");
    }
  }

  @Nested
  @DisplayName("전략 타입 테스트")
  class StrategyTypeTests {

    @Test
    @DisplayName("전략 타입이 DCA로 반환된다")
    void getStrategyType_ReturnsDCA() {
      // when
      StrategyType type = dcaStrategy.getStrategyType();

      // then
      assertThat(type).isEqualTo(StrategyType.DCA);
    }
  }

  @Nested
  @DisplayName("배당금 재투자 테스트")
  class DividendReinvestmentTests {

    @Test
    @DisplayName("재투자를 켜면 배당으로 추가 매수한다")
    void execute_WithDividendReinvestment_BuysExtraShares() {
      // given
      BigDecimal amountPerPurchase = new BigDecimal("1000.00");
      DcaStrategyRequest request = DcaStrategyRequest.builder()
        .userId(userId)
        .symbol(symbol)
        .startDate(LocalDate.of(2024, 1, 1))
        .endDate(LocalDate.of(2024, 3, 31))
        .amountPerPurchase(amountPerPurchase)
        .purchaseDay(15)
        .reinvestDividends(true)
        .build();

      // BULK API: Mock price data
      List<OHLCPriceDto> ohlcPrices = List.of(
        createOHLC(LocalDate.of(2024, 1, 15), new BigDecimal("100.00")),
        createOHLC(LocalDate.of(2024, 2, 15), new BigDecimal("100.00")),
        createOHLC(LocalDate.of(2024, 3, 15), new BigDecimal("100.00"))
      );
      given(marketDataClient.getOHLCPriceRange(eq(symbol), eq("2024-01-01"), eq("2024-03-31")))
        .willReturn(ohlcPrices);

      // BULK API: Mock FX rate
      java.util.Map<String, BigDecimal> fxRates = java.util.Map.of(
        "2024-01-15", new BigDecimal("1300.00"),
        "2024-02-15", new BigDecimal("1300.00"),
        "2024-03-15", new BigDecimal("1300.00")
      );
      given(marketDataClient.getBulkFxRates(any())).willReturn(fxRates);

      FxRateDto fxRate = new FxRateDto(
        LocalDate.now(), new BigDecimal("1300.00"));
      given(marketDataClient.getFxRate(anyString())).willReturn(fxRate);

      // 평가일 시세 Mock
      given(marketDataClient.getOHLCPrice(eq(symbol), anyString()))
        .willReturn(createOHLC(request.getEndDate(), new BigDecimal("100.00")));

      // 배당 2건(재투자로 거래가 3건에서 5건)
      given(marketDataClient.getDividendHistory(eq(symbol), anyString(), anyString()))
        .willReturn(List.of(
          new DividendDto(symbol, LocalDate.of(2024, 2, 15), null, null,
            new BigDecimal("1.00"), "USD", "test"),
          new DividendDto(symbol, LocalDate.of(2024, 3, 15), null, null,
            new BigDecimal("1.00"), "USD", "test")));

      // 배당일 범위 시세(재투자의 추가 매수 가격)
      given(marketDataClient.getOHLCPriceRange(eq(symbol), eq("2024-02-15"), eq("2024-03-15")))
        .willReturn(ohlcPrices);

      // Mock ResponseMapper
      StrategyResponse expectedResponse = StrategyResponse.builder()
        .strategyType(StrategyType.DCA)
        .build();
      given(responseMapper.toStrategyResponse(
        any(DcaStrategyRequest.class), any(), any(), any(), any()))
        .willReturn(expectedResponse);

      // when
      StrategyResponse result = dcaStrategy.execute(request);

      // then
      assertThat(result).isNotNull();

      ArgumentCaptor<StrategyCalculationResult> calcCaptor =
        ArgumentCaptor.forClass(StrategyCalculationResult.class);
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<StrategyTransaction>> txCaptor = ArgumentCaptor.forClass(List.class);
      verify(responseMapper).toStrategyResponse(
        any(DcaStrategyRequest.class), txCaptor.capture(), calcCaptor.capture(), any(), any());

      assertThat(calcCaptor.getValue().getDividendsReinvested()).isGreaterThan(BigDecimal.ZERO);
      assertThat(txCaptor.getValue()).hasSize(5);
    }

    @Test
    @DisplayName("재투자를 켜고 기간 끝에 팔면 재투자로 늘어난 주식까지 팔고 그 주식의 원가는 배당락일 주가 × 환율이다")
    void reinvestThenSellAtEnd() {
      // 매달 15일 130만원(1300원 · 100달러 → 10주), 2월 15일 주당 1달러 배당은 1월 10주 몫 → 0.1주, 2월 20일 110달러
      DcaStrategyRequest request = DcaStrategyRequest.builder().userId(userId).symbol(symbol)
        .startDate(LocalDate.of(2024, 1, 1)).endDate(LocalDate.of(2024, 2, 29))
        .amountPerPurchase(new BigDecimal("1300000")).purchaseDay(15).reinvestDividends(true)
        .periodEnd(PeriodEnd.SELL_AT_END).capitalGainsTax(true).build();
      OHLCPriceDto feb15 = createOHLC(LocalDate.of(2024, 2, 15), new BigDecimal("100.00"));
      given(marketDataClient.getOHLCPriceRange(eq(symbol), eq("2024-01-01"), eq("2024-02-29"))).willReturn(List.of(
        createOHLC(LocalDate.of(2024, 1, 15), new BigDecimal("100.00")), feb15,
        createOHLC(LocalDate.of(2024, 2, 20), new BigDecimal("110.00"))));
      given(marketDataClient.getOHLCPriceRange(eq(symbol), eq("2024-02-15"), eq("2024-02-15"))).willReturn(List.of(feb15));
      given(marketDataClient.getBulkFxRates(any())).willReturn(java.util.Map.of(
        "2024-01-15", new BigDecimal("1300"), "2024-02-15", new BigDecimal("1300"), "2024-02-20", new BigDecimal("1300")));
      given(marketDataClient.getFxRate(anyString())).willReturn(new FxRateDto(LocalDate.of(2024, 2, 29), new BigDecimal("1300")));
      given(marketDataClient.getOHLCPrice(eq(symbol), anyString()))
        .willReturn(createOHLC(LocalDate.of(2024, 2, 20), new BigDecimal("110.00")));
      given(marketDataClient.getDividendHistory(eq(symbol), anyString(), anyString())).willReturn(List.of(
        new DividendDto(symbol, LocalDate.of(2024, 2, 15), null, null, new BigDecimal("1.00"), "USD", "test")));

      StrategyResponse response = dcaStrategy.execute(request);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<StrategyTransaction>> txCaptor = ArgumentCaptor.forClass(List.class);
      verify(responseMapper).toStrategyResponse(any(DcaStrategyRequest.class), txCaptor.capture(), any(), any(), any());
      assertThat(txCaptor.getValue()).extracting(StrategyTransaction::getTrigger)
        .containsExactly("월정액", "월정액", "배당 재투자", "기간 끝 매도");
      assertThat(txCaptor.getValue().getLast().getShares()).isEqualByComparingTo("-20.1");
      // 20.1주 × 110달러 × 1300원 - (130만원 × 2 + 0.1주 × 100달러 × 1300원)
      assertThat(response.getCapitalGainsTax().years()).singleElement()
        .satisfies(year -> assertThat(year.realizedGainKrw()).isEqualByComparingTo("261300"));
    }
  }

  @Nested
  @DisplayName("투자 한도 테스트")
  class InvestmentLimitTests {

    private DcaStrategyRequest limited(String limit) {
      return DcaStrategyRequest.builder()
        .userId(userId)
        .symbol(symbol)
        .startDate(LocalDate.of(2024, 1, 1))
        .endDate(LocalDate.of(2024, 6, 30))
        .amountPerPurchase(new BigDecimal("300000"))
        .purchaseDay(15)
        .totalInvestmentLimit(new BigDecimal(limit))
        .build();
    }

    @ParameterizedTest(name = "회당 30만 · 예산 {0} → {1}회")
    @CsvSource({"1000000, 3", "900000, 3"})
    @DisplayName("예산을 넘는 회차부터 사지 않고 그 날짜부터 끝까지 예산 부족 구간 한 줄로 남긴다")
    void execute_WithInvestmentLimit_StopsBeforeExceeding(String limit, int rounds) {
      DcaStrategyRequest request = limited(limit);
      stubSixMonths(request);

      StrategyResponse response = dcaStrategy.execute(request);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<StrategyTransaction>> captor = ArgumentCaptor.forClass(List.class);
      verify(responseMapper).toStrategyResponse(any(DcaStrategyRequest.class), captor.capture(), any(), any(), any());
      assertThat(captor.getValue()).hasSize(rounds)
        .extracting(StrategyTransaction::getAmount).containsOnly(new BigDecimal("300000"));
      assertThat(response.getSkippedSpan())
        .isEqualTo(new SkippedSpan(LocalDate.of(2024, 4, 15), 6 - rounds, SkipReason.OVER_BUDGET));
    }

    @Test
    @DisplayName("예산이 회당 금액보다 작으면 투자 0 인 매수 0회 결과에 첫 회차부터의 예산 부족 구간을 싣는다")
    void limitBelowAmountReturnsZeroResult() {
      DcaStrategyRequest request = limited("200000");
      stubSixMonths(request);

      StrategyResponse response = dcaStrategy.execute(request);

      ArgumentCaptor<StrategyCalculationResult> calculation = ArgumentCaptor.forClass(StrategyCalculationResult.class);
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<StrategyTransaction>> transactions = ArgumentCaptor.forClass(List.class);
      verify(responseMapper).toStrategyResponse(any(DcaStrategyRequest.class), transactions.capture(),
        calculation.capture(), any(), any());
      assertThat(transactions.getValue()).isEmpty();
      assertThat(calculation.getValue().getTotalInvested()).isEqualByComparingTo("0");
      assertThat(calculation.getValue().getTotalReturnPercent()).isEqualByComparingTo("0");
      assertThat(response.getSkippedSpan())
        .isEqualTo(new SkippedSpan(LocalDate.of(2024, 1, 15), 6, SkipReason.OVER_BUDGET));
    }

    @Test
    @DisplayName("예산을 비우면 끝까지 사고 예산 부족 구간을 남기지 않는다")
    void noLimitBuysEveryRound() {
      DcaStrategyRequest request = limited("1");
      request.setTotalInvestmentLimit(null);
      stubSixMonths(request);

      StrategyResponse response = dcaStrategy.execute(request);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<StrategyTransaction>> captor = ArgumentCaptor.forClass(List.class);
      verify(responseMapper).toStrategyResponse(any(DcaStrategyRequest.class), captor.capture(), any(), any(), any());
      assertThat(captor.getValue()).hasSize(6);
      assertThat(response.getSkippedSpan()).isNull();
    }

    // 1 ~ 6월 15일 종가 100 과 평가일 시세 · 환율
    private void stubSixMonths(DcaStrategyRequest request) {
      List<OHLCPriceDto> ohlcPrices = List.of(
        createOHLC(LocalDate.of(2024, 1, 15), new BigDecimal("100.00")),
        createOHLC(LocalDate.of(2024, 2, 15), new BigDecimal("100.00")),
        createOHLC(LocalDate.of(2024, 3, 15), new BigDecimal("100.00")),
        createOHLC(LocalDate.of(2024, 4, 15), new BigDecimal("100.00")),
        createOHLC(LocalDate.of(2024, 5, 15), new BigDecimal("100.00")),
        createOHLC(LocalDate.of(2024, 6, 15), new BigDecimal("100.00"))
      );
      given(marketDataClient.getOHLCPriceRange(eq(symbol), eq("2024-01-01"), eq("2024-06-30")))
        .willReturn(ohlcPrices);
      given(marketDataClient.getBulkFxRates(any())).willReturn(java.util.Map.of());
      given(marketDataClient.getFxRate(anyString()))
        .willReturn(new FxRateDto(LocalDate.now(), new BigDecimal("1300.00")));
      given(marketDataClient.getOHLCPrice(eq(symbol), anyString()))
        .willReturn(createOHLC(request.getEndDate(), new BigDecimal("100.00")));
      lenient().when(marketDataClient.getDividendHistory(eq(symbol), anyString(), anyString()))
        .thenReturn(java.util.Collections.emptyList());
    }
  }

  @Nested
  @DisplayName("수수료")
  class FeeTests {

    @Test
    @DisplayName("매수마다 수수료를 차감하고 남은 달러로 산다. 투자금은 그대로 둔다")
    void execute_DeductsFeePerPurchase() {
      DcaStrategyRequest request = DcaStrategyRequest.builder()
        .userId(userId)
        .symbol(symbol)
        .startDate(startDate)
        .endDate(LocalDate.of(2024, 2, 29))
        .amountPerPurchase(new BigDecimal("1300000"))
        .purchaseDay(15)
        .tradingFeeRate(new BigDecimal("0.01"))
        .build();

      given(marketDataClient.getOHLCPriceRange(eq(symbol), eq("2024-01-01"), eq("2024-02-29")))
        .willReturn(List.of(
          createOHLC(LocalDate.of(2024, 1, 15), new BigDecimal("100.00")),
          createOHLC(LocalDate.of(2024, 2, 15), new BigDecimal("100.00"))));
      given(marketDataClient.getBulkFxRates(any())).willReturn(java.util.Map.of(
        "2024-01-15", new BigDecimal("1300"),
        "2024-02-15", new BigDecimal("1300")));
      given(marketDataClient.getFxRate(anyString()))
        .willReturn(new FxRateDto(LocalDate.now(), new BigDecimal("1300")));
      given(marketDataClient.getOHLCPrice(eq(symbol), anyString()))
        .willReturn(createOHLC(request.getEndDate(), new BigDecimal("100.00")));
      given(marketDataClient.getDividendHistory(eq(symbol), anyString(), anyString()))
        .willReturn(java.util.Collections.emptyList());
      given(responseMapper.toStrategyResponse(any(DcaStrategyRequest.class), any(), any(), any(), any()))
        .willReturn(StrategyResponse.builder().build());

      dcaStrategy.execute(request);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<StrategyTransaction>> captor = ArgumentCaptor.forClass(List.class);
      verify(responseMapper).toStrategyResponse(
        any(DcaStrategyRequest.class), captor.capture(), any(), any(), any());
      assertThat(captor.getValue()).hasSize(2).allSatisfy(tx -> {
        assertThat(tx.getFee()).isEqualByComparingTo("10.00");
        assertThat(tx.getShares()).isEqualByComparingTo("9.9");
        assertThat(tx.getAmount()).isEqualByComparingTo("1300000");
      });
    }
  }

  @Nested
  @DisplayName("주식 수")
  class SharesTests {

    private List<StrategyTransaction> executed() {
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<StrategyTransaction>> captor = ArgumentCaptor.forClass(List.class);
      verify(responseMapper).toStrategyResponse(any(DcaStrategyRequest.class), captor.capture(), any(), any(), any());
      return captor.getValue();
    }

    // 기간 시세 · 날마다 환율과 평가일 시세 · 환율(배당 없음)
    private void stubMarket(DcaStrategyRequest request, List<OHLCPriceDto> prices, java.util.Map<String, BigDecimal> fx) {
      given(marketDataClient.getOHLCPriceRange(symbol, request.getStartDate().toString(), request.getEndDate().toString()))
        .willReturn(prices);
      given(marketDataClient.getBulkFxRates(any())).willReturn(fx);
      given(marketDataClient.getOHLCPrice(eq(symbol), anyString())).willReturn(prices.getLast());
      given(marketDataClient.getFxRate(anyString())).willReturn(new FxRateDto(request.getEndDate(), new BigDecimal("1350")));
      lenient().when(marketDataClient.getDividendHistory(eq(symbol), anyString(), anyString())).thenReturn(List.of());
    }

    private DcaStrategyRequest.DcaStrategyRequestBuilder monthly(LocalDate end, int shares) {
      return DcaStrategyRequest.builder().userId(userId).symbol(symbol).startDate(LocalDate.of(2024, 1, 1)).endDate(end)
        .sharesPerPurchase(shares).purchaseDay(15);
    }

    @Test
    @DisplayName("회차마다 그 주식 수를 사고 넣은 돈은 종가 × 주식 수 + 수수료를 그 날짜 환율로 바꾼 값이다")
    void buysSharesPerRound() {
      DcaStrategyRequest request = monthly(LocalDate.of(2024, 2, 29), 2).tradingFeeRate(new BigDecimal("0.01")).build();
      stubMarket(request, List.of(createOHLC(LocalDate.of(2024, 1, 15), new BigDecimal("100.00")),
          createOHLC(LocalDate.of(2024, 2, 15), new BigDecimal("120.00"))),
        java.util.Map.of("2024-01-15", new BigDecimal("1300"), "2024-02-15", new BigDecimal("1350")));

      dcaStrategy.execute(request);

      // 1월 200달러 + 수수료 2달러 × 1300, 2월 240달러 + 수수료 2.40달러 × 1350
      List<StrategyTransaction> transactions = executed();
      assertThat(transactions).hasSize(2).allSatisfy(tx -> {
        assertThat(tx.getShares()).isEqualByComparingTo("2");
        assertThat(tx.getTrigger()).isEqualTo("매달 2주");
      });
      assertThat(transactions).extracting(StrategyTransaction::getFee).usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("2.00"), new BigDecimal("2.40"));
      assertThat(transactions).extracting(StrategyTransaction::getAmount).usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("262600"), new BigDecimal("327240"));
    }

    @Test
    @DisplayName("양도세를 켜도 판 거래가 없으면 연도 없이 세금 0 이고 세후 평가는 총 자산이다")
    void capitalGainsTaxWithoutSales() {
      DcaStrategyRequest request = monthly(LocalDate.of(2024, 2, 29), 2).capitalGainsTax(true).build();
      stubMarket(request, List.of(createOHLC(LocalDate.of(2024, 1, 15), new BigDecimal("100.00")),
          createOHLC(LocalDate.of(2024, 2, 15), new BigDecimal("125.00"))),
        java.util.Map.of("2024-01-15", new BigDecimal("1350"), "2024-02-15", new BigDecimal("1350")));

      StrategyResponse response = dcaStrategy.execute(request);

      // 27만원 + 33만 7,500원을 넣어 4주 × 125달러 × 1350원
      assertThat(response.getCapitalGainsTax().years()).isEmpty();
      assertThat(response.getCapitalGainsTax().totalTaxKrw()).isEqualByComparingTo("0");
      assertThat(response.getCapitalGainsTax().afterTaxValueKrw()).isEqualByComparingTo("675000");
      assertThat(response.getCapitalGainsTax().afterTaxReturnPercent()).isEqualByComparingTo("11.11");
    }

    @Test
    @DisplayName("기간 끝 매도를 고르면 마지막 거래일 종가에 수수료를 빼고 전량 팔고, 판 해의 원화 실현 이익으로 양도세를 계산하되 납부일이 기간 뒤라 내지 않는다")
    void sellAtEndWithCapitalGainsTax() {
      DcaStrategyRequest request = monthly(LocalDate.of(2024, 2, 29), 2).tradingFeeRate(new BigDecimal("0.01"))
        .periodEnd(PeriodEnd.SELL_AT_END).capitalGainsTax(true).build();
      stubMarket(request, List.of(createOHLC(LocalDate.of(2024, 1, 15), new BigDecimal("100.00")),
          createOHLC(LocalDate.of(2024, 2, 15), new BigDecimal("125.00"))),
        java.util.Map.of("2024-01-15", new BigDecimal("1350"), "2024-02-15", new BigDecimal("1350")));

      StrategyResponse response = dcaStrategy.execute(request);

      // 4주 × 125달러 - 수수료 5달러, 원가 (202 + 252.50달러) × 1350원
      assertThat(executed()).last().satisfies(sale -> {
        assertThat(sale.getTrigger()).isEqualTo("기간 끝 매도");
        assertThat(sale.tradedOn()).isEqualTo(LocalDate.of(2024, 2, 15));
        assertThat(sale.getShares()).isEqualByComparingTo("-4");
        assertThat(sale.getProceeds()).isEqualByComparingTo("495");
        assertThat(sale.getRealizedProfit()).isEqualByComparingTo("40.50");
      });
      assertThat(response.getCapitalGainsTax().years()).containsExactly(
        new CapitalGainsTax.Year(2024, new BigDecimal("54675"), new BigDecimal("54675"), new BigDecimal("0"), null,
          BigDecimal.ZERO));
      assertThat(executed()).noneMatch(StrategyTransaction::isTaxPayment);
      assertThat(response.getCapitalGainsTax().paidTaxKrw()).isEqualByComparingTo("0");
      // 판 돈 495달러 × 1350원
      assertThat(response.getCapitalGainsTax().cashKrw()).isEqualByComparingTo("668250");
      assertThat(response.getCapitalGainsTax().stockKrw()).isEqualByComparingTo("0");
      assertThat(response.getCapitalGainsTax().afterTaxValueKrw()).isEqualByComparingTo("668250");
    }

    @Test
    @DisplayName("기간 끝을 비우면 끝까지 보유해 팔지 않는다")
    void holdByDefault() {
      DcaStrategyRequest request = monthly(LocalDate.of(2024, 2, 29), 2).build();
      stubMarket(request, List.of(createOHLC(LocalDate.of(2024, 1, 15), new BigDecimal("100.00")),
          createOHLC(LocalDate.of(2024, 2, 15), new BigDecimal("125.00"))),
        java.util.Map.of("2024-01-15", new BigDecimal("1350"), "2024-02-15", new BigDecimal("1350")));

      dcaStrategy.execute(request);

      assertThat(executed()).hasSize(2).noneMatch(StrategyTransaction::isSell);
    }

    @Test
    @DisplayName("예산이 모자란 회차부터는 값이 내린 회차도 사지 않고 끝까지 예산 부족으로 센다")
    void shortfallRunsToEnd() {
      DcaStrategyRequest request = monthly(LocalDate.of(2024, 4, 30), 1)
        .totalInvestmentLimit(new BigDecimal("300000")).build();
      // 기본 환율 1350 에 1월 13만 5천원, 2월 20만 2,500원은 예산을 넘고 3월 6만 7,500원은 남은 예산 안
      stubMarket(request, List.of(createOHLC(LocalDate.of(2024, 1, 15), new BigDecimal("100")),
        createOHLC(LocalDate.of(2024, 2, 15), new BigDecimal("150")),
        createOHLC(LocalDate.of(2024, 3, 15), new BigDecimal("50")),
        createOHLC(LocalDate.of(2024, 4, 15), new BigDecimal("100"))), java.util.Map.of());

      StrategyResponse response = dcaStrategy.execute(request);

      assertThat(executed()).singleElement().satisfies(tx -> assertThat(tx.getAmount()).isEqualByComparingTo("135000"));
      assertThat(response.getSkippedSpan())
        .isEqualTo(new SkippedSpan(LocalDate.of(2024, 2, 15), 3, SkipReason.OVER_BUDGET));
    }
  }

  // 내부 메서드
  @Nested
  @DisplayName("못 산 회차")
  class SkippedRoundsTests {

    private DcaStrategyRequest request() {
      return DcaStrategyRequest.builder()
        .userId(userId)
        .symbol(symbol)
        .startDate(LocalDate.of(2024, 1, 1))
        .endDate(LocalDate.of(2024, 3, 31))
        .amountPerPurchase(new BigDecimal("1300000"))
        .purchaseDay(15)
        .build();
    }

    // 평가일 시세 · 환율 · 배당
    private void stubValuation() {
      given(marketDataClient.getFxRate(anyString()))
        .willReturn(new FxRateDto(LocalDate.now(), new BigDecimal("1300")));
      given(marketDataClient.getOHLCPrice(eq(symbol), anyString()))
        .willReturn(createOHLC(LocalDate.of(2024, 3, 29), new BigDecimal("100.00")));
      given(marketDataClient.getDividendHistory(eq(symbol), anyString(), anyString()))
        .willReturn(java.util.Collections.emptyList());
    }

    @SuppressWarnings("unchecked")
    private List<SkippedRound> skipped() {
      ArgumentCaptor<List<SkippedRound>> captor = ArgumentCaptor.forClass(List.class);
      verify(responseMapper).toStrategyResponse(any(DcaStrategyRequest.class), any(), any(), any(), captor.capture());
      return captor.getValue();
    }

    @Test
    @DisplayName("투자일에 시세가 없으면 시세 없음으로 세고 첫 매수일을 싣는다")
    void noPrice() {
      given(marketDataClient.getOHLCPriceRange(eq(symbol), eq("2024-01-01"), eq("2024-03-31")))
        .willReturn(List.of(createOHLC(LocalDate.of(2024, 3, 15), new BigDecimal("100.00"))));
      given(marketDataClient.getBulkFxRates(any()))
        .willReturn(java.util.Map.of("2024-03-15", new BigDecimal("1300")));
      stubValuation();

      dcaStrategy.execute(request());

      assertThat(skipped()).containsExactly(
        new SkippedRound(LocalDate.of(2024, 1, 15), SkipReason.NO_PRICE),
        new SkippedRound(LocalDate.of(2024, 2, 15), SkipReason.NO_PRICE));
    }

    @Test
    @DisplayName("투자일 종가가 0 이하인 깨진 시세도 시세 없음으로 센다")
    void brokenPrice() {
      given(marketDataClient.getOHLCPriceRange(eq(symbol), eq("2024-01-01"), eq("2024-03-31")))
        .willReturn(List.of(
          createOHLC(LocalDate.of(2024, 1, 15), BigDecimal.ZERO),
          createOHLC(LocalDate.of(2024, 2, 15), new BigDecimal("100.00")),
          createOHLC(LocalDate.of(2024, 3, 15), new BigDecimal("100.00"))));
      given(marketDataClient.getBulkFxRates(any())).willReturn(java.util.Map.of(
        "2024-02-15", new BigDecimal("1300"),
        "2024-03-15", new BigDecimal("1300")));
      stubValuation();

      dcaStrategy.execute(request());

      assertThat(skipped()).containsExactly(new SkippedRound(LocalDate.of(2024, 1, 15), SkipReason.NO_PRICE));
    }

    // 환율 0 은 수수료가 있으면 수수료 계산에서, 없으면 주식 수 계산에서 막힘
    @ParameterizedTest
    @ValueSource(strings = {"0.001", "0"})
    @DisplayName("한 회차 계산이 실패하면(1월 환율 0) 그 회차는 사지 않고 계산 오류로 남긴 뒤 다음 회차를 산다")
    void calculationFailure(String feeRate) {
      DcaStrategyRequest request = request();
      request.setTradingFeeRate(new BigDecimal(feeRate));
      given(marketDataClient.getOHLCPriceRange(eq(symbol), eq("2024-01-01"), eq("2024-03-31")))
        .willReturn(List.of(
          createOHLC(LocalDate.of(2024, 1, 15), new BigDecimal("100.00")),
          createOHLC(LocalDate.of(2024, 2, 15), new BigDecimal("100.00")),
          createOHLC(LocalDate.of(2024, 3, 15), new BigDecimal("100.00"))));
      given(marketDataClient.getBulkFxRates(any())).willReturn(java.util.Map.of(
        "2024-01-15", BigDecimal.ZERO,
        "2024-02-15", new BigDecimal("1300"),
        "2024-03-15", new BigDecimal("1300")));
      stubValuation();

      dcaStrategy.execute(request);

      assertThat(skipped()).containsExactly(new SkippedRound(LocalDate.of(2024, 1, 15), SkipReason.CALCULATION_ERROR));
    }
  }

  private OHLCPriceDto createOHLC(LocalDate date, BigDecimal closePrice) {
    return new OHLCPriceDto(
      symbol,
      date,
      closePrice.subtract(new BigDecimal("1.00")),  // openPrice
      closePrice.add(new BigDecimal("2.00")),       // highPrice
      closePrice.subtract(new BigDecimal("2.00")),  // lowPrice
      closePrice,                                    // closePrice
      null,                                          // adjustedClose
      1000000L,                                      // volume
      "USD",                                         // currency
      true                                           // available
    );
  }

  private StockPriceDto createStockPrice(BigDecimal price) {
    return new StockPriceDto(
      symbol,                    // symbol
      "Test Stock",              // name
      price,                     // currentPrice
      null,                      // previousClose
      null,                      // change
      null,                      // changePercent
      null,                      // volume
      null,                      // date
      null,                      // lastUpdated
      null,                      // openPrice
      null,                      // highPrice
      null,                      // lowPrice
      null,                      // closePrice
      null,                      // adjustedClose
      "USD",                     // currency
      true,                      // available
      null,                      // assetType
      null                       // marketCap
    );
  }

  private List<StrategyTransaction> createDcaTransactions() {
    List<StrategyTransaction> transactions = new ArrayList<>();
    transactions.add(StrategyTransaction.builder()
      .date(LocalDate.of(2024, 1, 15))
      .actualDate(LocalDate.of(2024, 1, 15))
      .trigger("월정액")
      .price(new BigDecimal("100.00"))
      .shares(new BigDecimal("10.00"))
      .amount(new BigDecimal("1000.00"))
      .fxRate(new BigDecimal("1300.00"))
      .build());
    transactions.add(StrategyTransaction.builder()
      .date(LocalDate.of(2024, 2, 15))
      .actualDate(LocalDate.of(2024, 2, 15))
      .trigger("월정액")
      .price(new BigDecimal("105.00"))
      .shares(new BigDecimal("9.52"))
      .amount(new BigDecimal("1000.00"))
      .fxRate(new BigDecimal("1300.00"))
      .build());
    transactions.add(StrategyTransaction.builder()
      .date(LocalDate.of(2024, 3, 15))
      .actualDate(LocalDate.of(2024, 3, 15))
      .trigger("월정액")
      .price(new BigDecimal("110.00"))
      .shares(new BigDecimal("9.09"))
      .amount(new BigDecimal("1000.00"))
      .fxRate(new BigDecimal("1300.00"))
      .build());
    return transactions;
  }

  private StrategyCalculationResult createMockCalculation() {
    return StrategyCalculationResult.builder()
      .totalInvested(new BigDecimal("1000.00"))
      .totalShares(new BigDecimal("10.00"))
      .averagePrice(new BigDecimal("100.00"))
      .currentValue(new BigDecimal("1000.00"))
      .currentValueKrw(new BigDecimal("1300000.00"))
      .totalReturnPercent(BigDecimal.ZERO)
      .totalReturnKrw(BigDecimal.ZERO)
      .build();
  }

  @Nested
  @DisplayName("요청 검증 · 투자 주기 · 미래 종료일")
  class ValidationAndScheduleTests {

    private DcaStrategyRequest.DcaStrategyRequestBuilder base() {
      return DcaStrategyRequest.builder().userId(userId).symbol(symbol)
        .startDate(LocalDate.of(2024, 1, 1)).endDate(LocalDate.of(2024, 5, 31))
        .amountPerPurchase(new BigDecimal("1300000")).purchaseDay(15);
    }

    private List<StrategyTransaction> executed() {
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<StrategyTransaction>> captor = ArgumentCaptor.forClass(List.class);
      verify(responseMapper).toStrategyResponse(any(DcaStrategyRequest.class), captor.capture(), any(), any(), any());
      return captor.getValue();
    }

    @ParameterizedTest(name = "회당 금액 {0}")
    @ValueSource(strings = {"0", "-1"})
    @DisplayName("회당 금액이 0 이하면 STRATEGY_AMOUNT_PER_PURCHASE_REQUIRED")
    void nonPositiveAmount(String amount) {
      DcaStrategyRequest request = base().amountPerPurchase(new BigDecimal(amount)).build();

      assertThatThrownBy(() -> dcaStrategy.execute(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.STRATEGY_AMOUNT_PER_PURCHASE_REQUIRED);
    }

    @ParameterizedTest(name = "주식 수 {0}")
    @CsvSource(value = {"null, AMOUNT_OR_SHARES_REQUIRED", "0, INVALID_SHARES_PER_PURCHASE", "1000001, INVALID_SHARES_PER_PURCHASE"},
      nullValues = "null")
    @DisplayName("회당 금액 · 주식 수가 둘 다 없으면 AMOUNT_OR_SHARES_REQUIRED, 주식 수가 1 ~ 1,000,000 밖이면 INVALID_SHARES_PER_PURCHASE")
    void missingAmountOrShares(Integer shares, BacktestResponse code) {
      DcaStrategyRequest request = base().amountPerPurchase(null).sharesPerPurchase(shares).build();

      assertThatThrownBy(() -> dcaStrategy.execute(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(code);
    }

    @Test
    @DisplayName("지수를 사는 요청은 시세를 받기 전에 INDEX_NOT_TRADABLE")
    void indexSymbol() {
      DcaStrategyRequest request = base().symbol("^GSPC").build();

      assertThatThrownBy(() -> dcaStrategy.execute(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.INDEX_NOT_TRADABLE);
      verifyNoInteractions(marketDataClient);
    }

    @Test
    @DisplayName("회당 금액 · 주식 수가 둘 다 있으면 AMOUNT_OR_SHARES_REQUIRED")
    void bothAmountAndShares() {
      DcaStrategyRequest request = base().sharesPerPurchase(1).build();

      assertThatThrownBy(() -> dcaStrategy.execute(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.AMOUNT_OR_SHARES_REQUIRED);
    }

    @ParameterizedTest(name = "예산 {0}")
    @ValueSource(strings = {"0", "-1"})
    @DisplayName("예산이 0 이하면 INVALID_TOTAL_INVESTMENT")
    void nonPositiveLimit(String limit) {
      DcaStrategyRequest request = base().totalInvestmentLimit(new BigDecimal(limit)).build();

      assertThatThrownBy(() -> dcaStrategy.execute(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.INVALID_TOTAL_INVESTMENT);
    }

    @ParameterizedTest(name = "첫 매수일 {0}")
    @ValueSource(strings = {"2023-12-31", "2024-06-01"})
    @DisplayName("첫 매수일이 시작일 ~ 종료일 밖이면 INVALID_FIRST_PURCHASE_DATE")
    void firstPurchaseDateOutOfPeriod(String first) {
      DcaStrategyRequest request = base().firstPurchaseDate(LocalDate.parse(first)).build();

      assertThatThrownBy(() -> dcaStrategy.execute(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.INVALID_FIRST_PURCHASE_DATE);
      verifyNoInteractions(marketDataClient);
    }

    @ParameterizedTest(name = "투자일 {0}")
    @ValueSource(ints = {0, 32})
    @DisplayName("투자일이 1 ~ 31 밖이면 INVALID_PURCHASE_DAY")
    void purchaseDayOutOfRange(int day) {
      DcaStrategyRequest request = base().purchaseDay(day).build();

      assertThatThrownBy(() -> dcaStrategy.execute(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.INVALID_PURCHASE_DAY);
    }

    @Test
    @DisplayName("투자일을 비우면 시작일의 날짜에 사고, 그 날짜가 휴장이면 다음 거래일에 산다")
    void missingPurchaseDayFollowsStartDate() {
      // 2월 10일은 토요일 · 3월 10일은 일요일
      given(marketDataClient.getOHLCPriceRange(symbol, "2024-01-10", "2024-03-31")).willReturn(List.of(
        createOHLC(LocalDate.of(2024, 1, 10), new BigDecimal("100")), createOHLC(LocalDate.of(2024, 2, 12), new BigDecimal("100")),
        createOHLC(LocalDate.of(2024, 3, 11), new BigDecimal("100"))));
      given(marketDataClient.getBulkFxRates(any())).willReturn(java.util.Map.of());
      given(marketDataClient.getOHLCPrice(eq(symbol), anyString())).willReturn(createOHLC(LocalDate.of(2024, 3, 29), new BigDecimal("100")));
      given(marketDataClient.getFxRate(anyString())).willReturn(new FxRateDto(LocalDate.of(2024, 3, 29), new BigDecimal("1300")));
      given(marketDataClient.getDividendHistory(eq(symbol), anyString(), anyString())).willReturn(List.of());

      dcaStrategy.execute(base().startDate(LocalDate.of(2024, 1, 10)).endDate(LocalDate.of(2024, 3, 31)).purchaseDay(null).build());

      assertThat(executed()).extracting(StrategyTransaction::getActualDate).containsExactly(
        LocalDate.of(2024, 1, 10), LocalDate.of(2024, 2, 12), LocalDate.of(2024, 3, 11));
    }

    @Test
    @DisplayName("시작일 날짜보다 앞선 투자일은 그 달을 건너뛰고 다음 달부터 산다")
    void purchaseDayBeforeStartSkipsFirstMonth() {
      given(marketDataClient.getOHLCPriceRange(symbol, "2024-01-25", "2024-03-31")).willReturn(List.of(
        createOHLC(LocalDate.of(2024, 1, 25), new BigDecimal("100")), createOHLC(LocalDate.of(2024, 2, 15), new BigDecimal("100")),
        createOHLC(LocalDate.of(2024, 3, 15), new BigDecimal("100"))));
      given(marketDataClient.getBulkFxRates(any())).willReturn(java.util.Map.of());
      given(marketDataClient.getOHLCPrice(eq(symbol), anyString())).willReturn(createOHLC(LocalDate.of(2024, 3, 29), new BigDecimal("100")));
      given(marketDataClient.getFxRate(anyString())).willReturn(new FxRateDto(LocalDate.of(2024, 3, 29), new BigDecimal("1300")));
      given(marketDataClient.getDividendHistory(eq(symbol), anyString(), anyString())).willReturn(List.of());

      dcaStrategy.execute(base().startDate(LocalDate.of(2024, 1, 25)).endDate(LocalDate.of(2024, 3, 31)).build());

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<SkippedRound>> skipped = ArgumentCaptor.forClass(List.class);
      verify(responseMapper).toStrategyResponse(any(DcaStrategyRequest.class), any(), any(), any(), skipped.capture());
      assertThat(skipped.getValue()).isEmpty();
      assertThat(executed()).extracting(StrategyTransaction::getDate).containsExactly(
        LocalDate.of(2024, 2, 15), LocalDate.of(2024, 3, 15));
    }

    @ParameterizedTest(name = "투자 주기 {0}")
    @ValueSource(ints = {0, -1, 13})
    @DisplayName("투자 주기가 1 ~ 12 밖이면 투자일을 세기 전에 INVALID_INVESTMENT_INTERVAL")
    void investmentIntervalOutOfRange(int interval) {
      DcaStrategyRequest request = base().investmentInterval(interval).build();

      assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
        assertThatThrownBy(() -> dcaStrategy.execute(request))
          .isInstanceOf(BacktestException.class)
          .extracting("code")
          .isEqualTo(BacktestResponse.INVALID_INVESTMENT_INTERVAL));
    }

    @Test
    @DisplayName("투자 주기가 2개월이면 1 · 3 · 5월에만 사고, 주기를 비우면 매달 산다")
    void investmentInterval() {
      given(marketDataClient.getOHLCPriceRange(symbol, "2024-01-01", "2024-05-31")).willReturn(List.of(
        createOHLC(LocalDate.of(2024, 1, 15), new BigDecimal("100")), createOHLC(LocalDate.of(2024, 2, 15), new BigDecimal("100")),
        createOHLC(LocalDate.of(2024, 3, 15), new BigDecimal("100")), createOHLC(LocalDate.of(2024, 4, 15), new BigDecimal("100")),
        createOHLC(LocalDate.of(2024, 5, 15), new BigDecimal("100"))));
      given(marketDataClient.getBulkFxRates(any())).willReturn(java.util.Map.of());
      given(marketDataClient.getOHLCPrice(eq(symbol), anyString())).willReturn(createOHLC(LocalDate.of(2024, 5, 31), new BigDecimal("100")));
      given(marketDataClient.getFxRate(anyString())).willReturn(new FxRateDto(LocalDate.of(2024, 5, 31), new BigDecimal("1300")));
      given(marketDataClient.getDividendHistory(eq(symbol), anyString(), anyString())).willReturn(List.of());
      given(responseMapper.toStrategyResponse(any(DcaStrategyRequest.class), any(), any(), any(), any()))
        .willReturn(StrategyResponse.builder().build());

      dcaStrategy.execute(base().investmentInterval(2).build());
      dcaStrategy.execute(base().investmentInterval(null).build());

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<StrategyTransaction>> captor = ArgumentCaptor.forClass(List.class);
      verify(responseMapper, times(2)).toStrategyResponse(any(DcaStrategyRequest.class), captor.capture(), any(), any(), any());
      assertThat(captor.getAllValues().get(0)).extracting(StrategyTransaction::getDate).containsExactly(
        LocalDate.of(2024, 1, 15), LocalDate.of(2024, 3, 15), LocalDate.of(2024, 5, 15));
      assertThat(captor.getAllValues().get(1)).hasSize(5);
    }

    @Test
    @DisplayName("투자일이 그 달에 없으면 말일에 사고, 환율은 기간의 날마다 한 번에 받는다")
    void purchaseDayClampedToMonthEnd() {
      DcaStrategyRequest request = base().endDate(LocalDate.of(2024, 4, 30)).purchaseDay(31).build();
      given(marketDataClient.getOHLCPriceRange(symbol, "2024-01-01", "2024-04-30")).willReturn(List.of(
        createOHLC(LocalDate.of(2024, 1, 31), new BigDecimal("100")), createOHLC(LocalDate.of(2024, 2, 29), new BigDecimal("100")),
        createOHLC(LocalDate.of(2024, 3, 31), new BigDecimal("100")), createOHLC(LocalDate.of(2024, 4, 30), new BigDecimal("100"))));
      given(marketDataClient.getBulkFxRates(any())).willReturn(java.util.Map.of());
      given(marketDataClient.getOHLCPrice(eq(symbol), anyString())).willReturn(createOHLC(LocalDate.of(2024, 4, 30), new BigDecimal("100")));
      given(marketDataClient.getFxRate(anyString())).willReturn(new FxRateDto(LocalDate.of(2024, 4, 30), new BigDecimal("1300")));
      given(marketDataClient.getDividendHistory(eq(symbol), anyString(), anyString())).willReturn(List.of());
      given(responseMapper.toStrategyResponse(any(DcaStrategyRequest.class), any(), any(), any(), any()))
        .willReturn(StrategyResponse.builder().build());

      dcaStrategy.execute(request);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<String>> fxDates = ArgumentCaptor.forClass(List.class);
      verify(marketDataClient).getBulkFxRates(fxDates.capture());
      // 첫 시세일 1월 31일 ~ 4월 30일 91일(사는 날 넷 포함)
      assertThat(fxDates.getValue()).hasSize(91).startsWith("2024-01-31").endsWith("2024-04-30")
        .contains("2024-01-31", "2024-02-29", "2024-03-31", "2024-04-30");
      assertThat(executed()).extracting(StrategyTransaction::getDate).containsExactly(
        LocalDate.of(2024, 1, 31), LocalDate.of(2024, 2, 29), LocalDate.of(2024, 3, 31), LocalDate.of(2024, 4, 30));
    }

    @Test
    @DisplayName("종료일이 오늘 이후면 오늘까지만 조회하고 현재가로 평가한다")
    void futureEndDate() {
      LocalDate today = LocalDate.now();
      LocalDate start = today.withDayOfMonth(1);
      DcaStrategyRequest request = base().startDate(start).endDate(today.plusMonths(3)).purchaseDay(1).build();
      given(marketDataClient.getOHLCPriceRange(symbol, start.toString(), today.toString()))
        .willReturn(List.of(createOHLC(start, new BigDecimal("100"))));
      given(marketDataClient.getBulkFxRates(any())).willReturn(java.util.Map.of());
      given(marketDataClient.getCurrentPrice(symbol)).willReturn(createStockPrice(new BigDecimal("110")));
      given(marketDataClient.getLatestFxRate()).willReturn(new FxRateDto(today, new BigDecimal("1300")));
      given(marketDataClient.getDividendHistory(eq(symbol), anyString(), anyString())).willReturn(List.of());
      given(responseMapper.toStrategyResponse(any(DcaStrategyRequest.class), any(), any(), any(), any()))
        .willReturn(StrategyResponse.builder().build());

      dcaStrategy.execute(request);

      assertThat(executed()).hasSize(1);
      verify(marketDataClient).getOHLCPriceRange(symbol, start.toString(), today.toString());
    }
  }

  @Nested
  @DisplayName("주기 · 첫 매수일")
  class FrequencyTests {

    // from ~ to 의 평일마다 종가 100 시세(closed 날짜는 휴장)
    private List<OHLCPriceDto> weekdays(LocalDate from, LocalDate to, LocalDate... closed) {
      List<LocalDate> holidays = List.of(closed);
      List<OHLCPriceDto> prices = new ArrayList<>();
      for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
        if (d.getDayOfWeek().getValue() <= 5 && !holidays.contains(d)) {
          prices.add(createOHLC(d, new BigDecimal("100")));
        }
      }
      return prices;
    }

    // 기간 시세와 평가일 시세 · 환율(배당 없음)
    private void stubMarket(LocalDate start, LocalDate end, List<OHLCPriceDto> prices) {
      given(marketDataClient.getOHLCPriceRange(symbol, start.toString(), end.toString())).willReturn(prices);
      given(marketDataClient.getBulkFxRates(any())).willReturn(java.util.Map.of());
      given(marketDataClient.getOHLCPrice(eq(symbol), anyString())).willReturn(createOHLC(end, new BigDecimal("100")));
      given(marketDataClient.getFxRate(anyString())).willReturn(new FxRateDto(end, new BigDecimal("1300")));
      lenient().when(marketDataClient.getDividendHistory(eq(symbol), anyString(), anyString())).thenReturn(List.of());
    }

    // 매일 · 매주에서는 쓰지 않는 투자일 · 투자 주기를 채운 요청
    private DcaStrategyRequest.DcaStrategyRequestBuilder request(DcaFrequency frequency, LocalDate start, LocalDate end) {
      return DcaStrategyRequest.builder().userId(userId).symbol(symbol).startDate(start).endDate(end)
        .amountPerPurchase(new BigDecimal("1300000")).frequency(frequency).purchaseDay(15).investmentInterval(3);
    }

    private List<StrategyTransaction> executed() {
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<StrategyTransaction>> captor = ArgumentCaptor.forClass(List.class);
      verify(responseMapper).toStrategyResponse(any(DcaStrategyRequest.class), captor.capture(), any(), any(), any());
      return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<SkippedRound> skipped() {
      ArgumentCaptor<List<SkippedRound>> captor = ArgumentCaptor.forClass(List.class);
      verify(responseMapper).toStrategyResponse(any(DcaStrategyRequest.class), any(), any(), any(), captor.capture());
      return captor.getValue();
    }

    @Test
    @DisplayName("매일은 첫 매수일부터 거래일마다 사고 거래에 매일 정액을 남긴다")
    void daily() {
      LocalDate start = LocalDate.of(2024, 6, 3);
      LocalDate end = LocalDate.of(2024, 6, 11);
      stubMarket(start, end, weekdays(start, end));

      dcaStrategy.execute(request(DcaFrequency.DAILY, start, end).firstPurchaseDate(LocalDate.of(2024, 6, 6)).build());

      assertThat(executed()).hasSize(4).allSatisfy(tx -> {
        assertThat(tx.getDate()).isEqualTo(tx.getActualDate());
        assertThat(tx.getTrigger()).isEqualTo("매일 정액");
      }).extracting(StrategyTransaction::getDate).containsExactly(
        LocalDate.of(2024, 6, 6), LocalDate.of(2024, 6, 7), LocalDate.of(2024, 6, 10), LocalDate.of(2024, 6, 11));
    }

    @Test
    @DisplayName("매주는 첫 매수일의 요일마다 사고, 그 요일이 휴장이면 그 주 다음 거래일에 산다")
    void weeklyOnFirstPurchaseWeekday() {
      // 6월 5일은 수요일, 6월 19일 수요일은 휴장
      LocalDate start = LocalDate.of(2024, 6, 1);
      LocalDate end = LocalDate.of(2024, 6, 30);
      stubMarket(start, end, weekdays(start, end, LocalDate.of(2024, 6, 19)));

      dcaStrategy.execute(request(DcaFrequency.WEEKLY, start, end).firstPurchaseDate(LocalDate.of(2024, 6, 5)).build());

      List<StrategyTransaction> transactions = executed();
      assertThat(transactions).extracting(StrategyTransaction::getDate).containsExactly(
        LocalDate.of(2024, 6, 5), LocalDate.of(2024, 6, 12), LocalDate.of(2024, 6, 19), LocalDate.of(2024, 6, 26));
      assertThat(transactions).extracting(StrategyTransaction::getActualDate).containsExactly(
        LocalDate.of(2024, 6, 5), LocalDate.of(2024, 6, 12), LocalDate.of(2024, 6, 20), LocalDate.of(2024, 6, 26));
      assertThat(transactions).extracting(StrategyTransaction::getTrigger).containsOnly("매주 정액");
    }

    @Test
    @DisplayName("첫 매수일을 비우면 시작일부터 세고, 토요일 시작이면 월요일마다 사며 기간 끝에 걸려 거래일이 없는 주는 세지 않는다")
    void weeklyFromWeekendStart() {
      // 6월 1일 · 29일은 토요일, 30일은 일요일
      LocalDate start = LocalDate.of(2024, 6, 1);
      LocalDate end = LocalDate.of(2024, 6, 30);
      stubMarket(start, end, weekdays(start, end));

      dcaStrategy.execute(request(DcaFrequency.WEEKLY, start, end).build());

      assertThat(executed()).extracting(StrategyTransaction::getActualDate).containsExactly(
        LocalDate.of(2024, 6, 3), LocalDate.of(2024, 6, 10), LocalDate.of(2024, 6, 17), LocalDate.of(2024, 6, 24));
      assertThat(skipped()).isEmpty();
    }

    @Test
    @DisplayName("매주 한 주 내내 시세가 없으면 그 주는 시세 없음으로 센다")
    void weeklyWithoutPricesForWholeWeek() {
      LocalDate start = LocalDate.of(2024, 6, 3);
      LocalDate end = LocalDate.of(2024, 6, 28);
      List<OHLCPriceDto> prices = new ArrayList<>(weekdays(start, LocalDate.of(2024, 6, 7)));
      prices.addAll(weekdays(LocalDate.of(2024, 6, 17), end));
      stubMarket(start, end, prices);

      dcaStrategy.execute(request(DcaFrequency.WEEKLY, start, end).build());

      assertThat(skipped()).containsExactly(new SkippedRound(LocalDate.of(2024, 6, 10), SkipReason.NO_PRICE));
      assertThat(executed()).extracting(StrategyTransaction::getActualDate).containsExactly(
        LocalDate.of(2024, 6, 3), LocalDate.of(2024, 6, 17), LocalDate.of(2024, 6, 24));
    }

    @Test
    @DisplayName("매달은 첫 매수일이 있는 달부터 사고, 투자일을 비우면 첫 매수일의 날짜에 산다")
    void monthlyFromFirstPurchaseDate() {
      // 4월 20일은 토요일
      LocalDate start = LocalDate.of(2024, 1, 1);
      LocalDate end = LocalDate.of(2024, 5, 31);
      stubMarket(start, end, weekdays(start, end));

      dcaStrategy.execute(request(DcaFrequency.MONTHLY, start, end).purchaseDay(null).investmentInterval(1)
        .firstPurchaseDate(LocalDate.of(2024, 2, 20)).build());

      List<StrategyTransaction> transactions = executed();
      assertThat(transactions).extracting(StrategyTransaction::getActualDate).containsExactly(
        LocalDate.of(2024, 2, 20), LocalDate.of(2024, 3, 20), LocalDate.of(2024, 4, 22), LocalDate.of(2024, 5, 20));
      assertThat(transactions).extracting(StrategyTransaction::getTrigger).containsOnly("월정액");
    }

    @Test
    @DisplayName("주기를 비우면 매달로 산다")
    void missingFrequencyIsMonthly() {
      LocalDate start = LocalDate.of(2024, 1, 1);
      LocalDate end = LocalDate.of(2024, 3, 31);
      stubMarket(start, end, weekdays(start, end));

      dcaStrategy.execute(request(null, start, end).investmentInterval(1).build());

      assertThat(executed()).extracting(StrategyTransaction::getActualDate).containsExactly(
        LocalDate.of(2024, 1, 15), LocalDate.of(2024, 2, 15), LocalDate.of(2024, 3, 15));
    }

    @Test
    @DisplayName("매달 투자일이 휴장이면 그 달 안 다음 거래일, 그 달에 없으면 그 앞 마지막 거래일에 사서 한 달에 한 번만 산다")
    void monthlyOncePerMonth() {
      // 2015년 1월 31일 · 2월 28일은 토요일
      LocalDate start = LocalDate.of(2015, 1, 1);
      LocalDate end = LocalDate.of(2015, 4, 30);
      stubMarket(start, end, weekdays(start, end));

      dcaStrategy.execute(request(DcaFrequency.MONTHLY, start, end).purchaseDay(31).investmentInterval(1).build());

      List<StrategyTransaction> transactions = executed();
      assertThat(transactions).extracting(StrategyTransaction::getDate).containsExactly(
        LocalDate.of(2015, 1, 31), LocalDate.of(2015, 2, 28), LocalDate.of(2015, 3, 31), LocalDate.of(2015, 4, 30));
      assertThat(transactions).extracting(StrategyTransaction::getActualDate).containsExactly(
        LocalDate.of(2015, 1, 30), LocalDate.of(2015, 2, 27), LocalDate.of(2015, 3, 31), LocalDate.of(2015, 4, 30));
    }

    @Test
    @DisplayName("첫 매수일 뒤 그 달에 거래일이 없으면 그 달은 세지 않고 다음 달 투자일부터 산다")
    void monthlyFirstPurchaseAfterLastTradingDay() {
      LocalDate start = LocalDate.of(2015, 1, 1);
      LocalDate end = LocalDate.of(2015, 3, 31);
      stubMarket(start, end, weekdays(start, end));

      dcaStrategy.execute(request(DcaFrequency.MONTHLY, start, end).purchaseDay(null).investmentInterval(1)
        .firstPurchaseDate(LocalDate.of(2015, 1, 31)).build());

      assertThat(executed()).extracting(StrategyTransaction::getActualDate).containsExactly(
        LocalDate.of(2015, 2, 27), LocalDate.of(2015, 3, 31));
      assertThat(skipped()).isEmpty();
    }

    @Test
    @DisplayName("종료일에 걸린 마지막 달에 투자일 뒤 거래일이 없으면 그 달은 세지 않고 앞 달에 두 번 사지 않는다")
    void monthlyLastMonthWithoutTradingDay() {
      // 1월 1일은 휴장, 2월 1일은 일요일
      LocalDate start = LocalDate.of(2015, 1, 1);
      LocalDate end = LocalDate.of(2015, 2, 1);
      stubMarket(start, end, weekdays(start, end, LocalDate.of(2015, 1, 1)));

      dcaStrategy.execute(request(DcaFrequency.MONTHLY, start, end).purchaseDay(1).investmentInterval(1).build());

      assertThat(executed()).extracting(StrategyTransaction::getActualDate).containsExactly(LocalDate.of(2015, 1, 2));
      assertThat(skipped()).isEmpty();
    }

    @Test
    @DisplayName("종료일에 걸린 달에 투자일부터 종료일까지 거래일이 없으면 투자일 앞 거래일에 사지 않고 그 달은 세지 않는다")
    void monthlyEndMonthWithoutTradingDayBeforeEnd() {
      // 2024년 6월 15일은 토요일, 16일은 일요일
      LocalDate start = LocalDate.of(2024, 5, 1);
      LocalDate end = LocalDate.of(2024, 6, 16);
      stubMarket(start, end, weekdays(start, end));

      dcaStrategy.execute(request(DcaFrequency.MONTHLY, start, end).investmentInterval(1).build());

      assertThat(executed()).extracting(StrategyTransaction::getActualDate).containsExactly(LocalDate.of(2024, 5, 15));
      assertThat(skipped()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(DcaFrequency.class)
    @DisplayName("종료일이 미래이고 첫 매수일이 오늘 뒤면 산 날 없이 매수 0회 결과를 돌려준다")
    void firstPurchaseAfterToday(DcaFrequency frequency) {
      LocalDate today = LocalDate.now();
      LocalDate start = today.minusDays(10);
      given(marketDataClient.getOHLCPriceRange(symbol, start.toString(), today.toString()))
        .willReturn(List.of(createOHLC(start, new BigDecimal("100"))));
      given(marketDataClient.getBulkFxRates(any())).willReturn(java.util.Map.of());
      given(marketDataClient.getCurrentPrice(symbol)).willReturn(createStockPrice(new BigDecimal("100")));
      given(marketDataClient.getLatestFxRate()).willReturn(new FxRateDto(today, new BigDecimal("1300")));

      dcaStrategy.execute(request(frequency, start, today.plusDays(30)).firstPurchaseDate(today.plusDays(5)).build());

      assertThat(executed()).isEmpty();
    }
  }

  @Nested
  @DisplayName("요청 타입 검사")
  class RequestTypeTests {

    @Test
    @DisplayName("조건부 매수 요청이 오면 전략 타입 불일치로 막는다")
    void execute_OtherRequestType_Rejected() {
      ConditionalStrategyRequest other = ConditionalStrategyRequest.builder().symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1)).endDate(LocalDate.of(2024, 1, 1)).build();

      assertThatThrownBy(() -> dcaStrategy.execute(other))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.STRATEGY_TYPE_MISMATCH);
      verifyNoInteractions(marketDataClient);
    }
  }
}
