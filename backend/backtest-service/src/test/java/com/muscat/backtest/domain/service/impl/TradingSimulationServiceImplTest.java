package com.muscat.backtest.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;

import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.BacktestType;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.util.BacktestHistoryUtils;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.response.CapitalGainsTax;
import com.muscat.backtest.domain.dto.response.DailyValue;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import com.muscat.backtest.infra.client.dto.DividendDto;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.dto.StockPriceDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.muscat.backtest.domain.dto.response.PurchaseDayReturn;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradingSimulationService 단위 테스트")
class TradingSimulationServiceImplTest {

  @Mock
  private MarketDataClientWrapper marketDataClientWrapper;

  @Mock
  private ResponseMapper responseMapper;

  @Mock
  private BacktestHistoryUtils backtestHistoryUtils;

  @InjectMocks
  private TradingSimulationServiceImpl tradingSimulationService;

  private static final String TEST_USER_ID = "test-user@example.com";
  private static final String TEST_SYMBOL = "AAPL";

  private SimulationRequest testSimulationRequest;

  @BeforeEach
  void setUp() {
    testSimulationRequest = SimulationRequest.builder()
      .symbol(TEST_SYMBOL)
      .purchaseDate(LocalDate.of(2024, 1, 15))
      .investmentAmount(new BigDecimal("1000000"))
      .tradingFeeRate(new BigDecimal("0.0025"))
      .userId(TEST_USER_ID)
      .reinvestDividends(false)
      .dividendTaxRate(BigDecimal.ZERO)
      .build();
    lenient().when(marketDataClientWrapper.defaultFxRate()).thenReturn(new BigDecimal("1350"));
    // 기간 환율은 요청한 날마다 1350
    lenient().when(marketDataClientWrapper.getBulkFxRates(anyList())).thenAnswer(invocation -> {
      List<String> dates = invocation.getArgument(0);
      return dates.stream().distinct().collect(Collectors.toMap(date -> date, date -> new BigDecimal("1350")));
    });
  }

  @Nested
  @DisplayName("시뮬레이션 실행 테스트")
  class RunSimulationTests {

    @Test
    @DisplayName("산 날 환율이 없어 기본 환율로 계산하면 그 날을 응답에 싣고, 받은 평가일 환율은 넣지 않는다")
    void runSimulation_DefaultPurchaseFx_ListsDate() {
      OHLCPriceDto purchasePrice = createOHLCPrice(TEST_SYMBOL,
        testSimulationRequest.getPurchaseDate(), new BigDecimal("180.00"), true);
      given(marketDataClientWrapper.getFxRate(anyString()))
        .willReturn(new FxRateDto(testSimulationRequest.getPurchaseDate(), new BigDecimal("1350"), true));
      given(marketDataClientWrapper.getCurrentPrice(TEST_SYMBOL))
        .willReturn(createCurrentPrice(TEST_SYMBOL, new BigDecimal("230.00")));
      given(marketDataClientWrapper.getLatestFxRate())
        .willReturn(new FxRateDto(LocalDate.now(), new BigDecimal("1320.00")));
      given(marketDataClientWrapper.getDividendHistory(eq(TEST_SYMBOL), anyString(), anyString()))
        .willReturn(Collections.emptyList());
      given(marketDataClientWrapper.getOHLCPriceRange(eq(TEST_SYMBOL), anyString(), anyString()))
        .willReturn(List.of(purchasePrice));
      given(responseMapper.toSimulationResponse(
        any(SimulationRequest.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(LocalDate.class), any(BigDecimal.class),
        any(LocalDate.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        anyList()))
        .willReturn(createSimulationResponse());

      SimulationResponse result = tradingSimulationService.runSimulation(testSimulationRequest);

      assertThat(result.getDefaultFxDates()).containsExactly(testSimulationRequest.getPurchaseDate());
    }

    @Test
    @DisplayName("기본 runSimulation 메서드는 recordHistory=true로 위임한다")
    void runSimulation_DefaultMethod_DelegatesToOverloaded() {
      // given
      OHLCPriceDto purchasePrice = createOHLCPrice(TEST_SYMBOL,
        testSimulationRequest.getPurchaseDate(),
        new BigDecimal("180.00"), true);
      StockPriceDto currentPrice = createCurrentPrice(TEST_SYMBOL, new BigDecimal("230.00"));
      FxRateDto purchaseFxRate = new FxRateDto(
        testSimulationRequest.getPurchaseDate(), new BigDecimal("1300.00"));
      FxRateDto currentFxRate = new FxRateDto(
        LocalDate.now(), new BigDecimal("1320.00"));

      SimulationResponse expectedResponse = createSimulationResponse();

      given(marketDataClientWrapper.getFxRate(anyString()))
        .willReturn(purchaseFxRate);
      given(marketDataClientWrapper.getCurrentPrice(TEST_SYMBOL))
        .willReturn(currentPrice);
      given(marketDataClientWrapper.getLatestFxRate())
        .willReturn(currentFxRate);
      given(marketDataClientWrapper.getDividendHistory(eq(TEST_SYMBOL), anyString(), anyString()))
        .willReturn(Collections.emptyList());
      given(marketDataClientWrapper.getOHLCPriceRange(eq(TEST_SYMBOL), anyString(), anyString()))
        .willReturn(List.of(purchasePrice));
      given(responseMapper.toSimulationResponse(
        any(SimulationRequest.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(LocalDate.class), any(BigDecimal.class),
        any(LocalDate.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        anyList()))
        .willReturn(expectedResponse);

      // when
      SimulationResponse result = tradingSimulationService.runSimulation(testSimulationRequest);

      // then
      assertThat(result).isNotNull();
      verify(backtestHistoryUtils).saveBacktestHistory(TEST_USER_ID, BacktestType.COMPARISON,
        testSimulationRequest);
    }

    @Test
    @DisplayName("시뮬레이션이 성공적으로 실행되고 히스토리가 기록된다")
    void runSimulation_WithRecordHistory_Success() {
      // given
      OHLCPriceDto purchasePrice = createOHLCPrice(TEST_SYMBOL,
        testSimulationRequest.getPurchaseDate(),
        new BigDecimal("180.00"), true);
      StockPriceDto currentPrice = createCurrentPrice(TEST_SYMBOL, new BigDecimal("230.00"));
      FxRateDto purchaseFxRate = new FxRateDto(
        testSimulationRequest.getPurchaseDate(), new BigDecimal("1300.00"));
      FxRateDto currentFxRate = new FxRateDto(
        LocalDate.now(), new BigDecimal("1320.00"));

      SimulationResponse expectedResponse = createSimulationResponse();

      given(marketDataClientWrapper.getFxRate(anyString()))
        .willReturn(purchaseFxRate);
      given(marketDataClientWrapper.getCurrentPrice(TEST_SYMBOL))
        .willReturn(currentPrice);
      given(marketDataClientWrapper.getLatestFxRate())
        .willReturn(currentFxRate);
      given(marketDataClientWrapper.getDividendHistory(eq(TEST_SYMBOL), anyString(), anyString()))
        .willReturn(Collections.emptyList());
      given(marketDataClientWrapper.getOHLCPriceRange(eq(TEST_SYMBOL), anyString(), anyString()))
        .willReturn(List.of(purchasePrice));
      given(responseMapper.toSimulationResponse(
        any(SimulationRequest.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(LocalDate.class), any(BigDecimal.class),
        any(LocalDate.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        anyList()))
        .willReturn(expectedResponse);

      // when
      SimulationResponse result = tradingSimulationService.runSimulation(testSimulationRequest);

      // then
      assertThat(result).isNotNull();
      assertThat(result).isEqualTo(expectedResponse);
      verify(backtestHistoryUtils).saveBacktestHistory(TEST_USER_ID, BacktestType.COMPARISON,
        testSimulationRequest);
      verify(marketDataClientWrapper, never()).getOHLCPrice(any(), any());
      verify(marketDataClientWrapper).getCurrentPrice(TEST_SYMBOL);
    }

    @Test
    @DisplayName("배당 재투자 시 배당일 주가는 BULK(getOHLCPriceRange)로 조회해 N+1을 막는다")
    void runSimulation_DividendReinvest_UsesBulkPriceLookup() {
      // given: 배당 재투자 ON + 배당 2건
      SimulationRequest reinvestRequest = SimulationRequest.builder()
        .symbol(TEST_SYMBOL)
        .purchaseDate(LocalDate.of(2024, 1, 15))
        .investmentAmount(new BigDecimal("1000000"))
        .tradingFeeRate(new BigDecimal("0.0025"))
        .userId(TEST_USER_ID)
        .reinvestDividends(true)
        .dividendTaxRate(BigDecimal.ZERO)
        .build();

      OHLCPriceDto purchasePrice = createOHLCPrice(TEST_SYMBOL,
        reinvestRequest.getPurchaseDate(), new BigDecimal("180.00"), true);
      OHLCPriceDto divDay1 = createOHLCPrice(TEST_SYMBOL, LocalDate.of(2024, 3, 15),
        new BigDecimal("200.00"), true);
      OHLCPriceDto divDay2 = createOHLCPrice(TEST_SYMBOL, LocalDate.of(2024, 6, 15),
        new BigDecimal("210.00"), true);

      given(marketDataClientWrapper.getFxRate(anyString()))
        .willReturn(new FxRateDto(reinvestRequest.getPurchaseDate(), new BigDecimal("1300.00")));
      given(marketDataClientWrapper.getCurrentPrice(TEST_SYMBOL))
        .willReturn(createCurrentPrice(TEST_SYMBOL, new BigDecimal("230.00")));
      given(marketDataClientWrapper.getLatestFxRate())
        .willReturn(new FxRateDto(LocalDate.now(), new BigDecimal("1320.00")));
      given(marketDataClientWrapper.getDividendHistory(eq(TEST_SYMBOL), anyString(), anyString()))
        .willReturn(List.of(
          new DividendDto(TEST_SYMBOL, LocalDate.of(2024, 3, 15), LocalDate.of(2024, 3, 20),
            null, new BigDecimal("0.50"), "USD", "test"),
          new DividendDto(TEST_SYMBOL, LocalDate.of(2024, 6, 15), LocalDate.of(2024, 6, 20),
            null, new BigDecimal("0.50"), "USD", "test")));
      // BULK: 배당일 주가 + 최적타이밍 모두 이 stub으로 (배당일 2건 포함)
      given(marketDataClientWrapper.getOHLCPriceRange(eq(TEST_SYMBOL), anyString(), anyString()))
        .willReturn(List.of(purchasePrice, divDay1, divDay2));
      given(responseMapper.toSimulationResponse(
        any(SimulationRequest.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(LocalDate.class), any(BigDecimal.class),
        any(LocalDate.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        anyList()))
        .willReturn(createSimulationResponse());

      // when
      SimulationResponse result = tradingSimulationService.runSimulation(reinvestRequest);

      // then: 매수가 · 배당일 주가 모두 기간 일봉에서 찾아 날짜별 시세 조회 없음
      assertThat(result).isNotNull();
      verify(marketDataClientWrapper, never()).getOHLCPrice(any(), any());
      verify(marketDataClientWrapper, atLeastOnce())
        .getOHLCPriceRange(eq(TEST_SYMBOL), anyString(), anyString());
    }

    @Test
    @DisplayName("recordHistory=false일 때 히스토리가 기록되지 않는다")
    void runSimulation_WithoutRecordHistory_NoHistorySaved() {
      // given
      OHLCPriceDto purchasePrice = createOHLCPrice(TEST_SYMBOL,
        testSimulationRequest.getPurchaseDate(),
        new BigDecimal("180.00"), true);
      StockPriceDto currentPrice = createCurrentPrice(TEST_SYMBOL, new BigDecimal("230.00"));
      FxRateDto purchaseFxRate = new FxRateDto(
        testSimulationRequest.getPurchaseDate(), new BigDecimal("1300.00"));
      FxRateDto currentFxRate = new FxRateDto(
        LocalDate.now(), new BigDecimal("1320.00"));

      SimulationResponse expectedResponse = createSimulationResponse();

      given(marketDataClientWrapper.getFxRate(anyString()))
        .willReturn(purchaseFxRate);
      given(marketDataClientWrapper.getCurrentPrice(TEST_SYMBOL))
        .willReturn(currentPrice);
      given(marketDataClientWrapper.getLatestFxRate())
        .willReturn(currentFxRate);
      given(marketDataClientWrapper.getDividendHistory(eq(TEST_SYMBOL), anyString(), anyString()))
        .willReturn(Collections.emptyList());
      given(marketDataClientWrapper.getOHLCPriceRange(eq(TEST_SYMBOL), anyString(), anyString()))
        .willReturn(List.of(purchasePrice));
      given(responseMapper.toSimulationResponse(
        any(SimulationRequest.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(LocalDate.class), any(BigDecimal.class),
        any(LocalDate.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        anyList()))
        .willReturn(expectedResponse);

      // when
      SimulationResponse result = tradingSimulationService.runSimulation(testSimulationRequest,
        false);

      // then
      assertThat(result).isNotNull();
      verify(backtestHistoryUtils, never()).saveBacktestHistory(any(), any(), any());
    }

    @Test
    @DisplayName("수동 환율을 사용한 시뮬레이션이 성공한다")
    void runSimulation_WithManualFxRates_Success() {
      // given
      BigDecimal manualPurchaseFxRate = new BigDecimal("1280.00");
      BigDecimal manualCurrentFxRate = new BigDecimal("1350.00");

      SimulationRequest requestWithManualFxRates = SimulationRequest.builder()
        .symbol(TEST_SYMBOL)
        .purchaseDate(LocalDate.of(2024, 1, 15))
        .investmentAmount(new BigDecimal("1000000"))
        .userId(TEST_USER_ID)
        .purchaseFxRate(manualPurchaseFxRate)
        .currentFxRate(manualCurrentFxRate)
        .build();

      OHLCPriceDto purchasePrice = createOHLCPrice(TEST_SYMBOL,
        requestWithManualFxRates.getPurchaseDate(),
        new BigDecimal("180.00"), true);
      StockPriceDto currentPrice = createCurrentPrice(TEST_SYMBOL, new BigDecimal("230.00"));
      SimulationResponse expectedResponse = createSimulationResponse();

      given(marketDataClientWrapper.getCurrentPrice(TEST_SYMBOL))
        .willReturn(currentPrice);
      given(marketDataClientWrapper.getDividendHistory(eq(TEST_SYMBOL), anyString(), anyString()))
        .willReturn(Collections.emptyList());
      given(marketDataClientWrapper.getOHLCPriceRange(eq(TEST_SYMBOL), anyString(), anyString()))
        .willReturn(List.of(purchasePrice));
      given(responseMapper.toSimulationResponse(
        any(SimulationRequest.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(LocalDate.class), any(BigDecimal.class),
        any(LocalDate.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        anyList()))
        .willReturn(expectedResponse);

      // when
      SimulationResponse result = tradingSimulationService.runSimulation(requestWithManualFxRates);

      // then
      assertThat(result).isNotNull();
      // 수동 환율 설정 시 자동 환율 조회를 호출하지 않음
      verify(marketDataClientWrapper, never()).getFxRate(anyString());
      verify(marketDataClientWrapper, never()).getLatestFxRate();
    }

    @Test
    @DisplayName("userId가 null일 때 히스토리가 기록되지 않는다")
    void runSimulation_WithNullUserId_NoHistorySaved() {
      // given
      SimulationRequest requestWithoutUserId = SimulationRequest.builder()
        .symbol(TEST_SYMBOL)
        .purchaseDate(LocalDate.of(2024, 1, 15))
        .investmentAmount(new BigDecimal("1000000"))
        .userId(null) // userId is null
        .build();

      OHLCPriceDto purchasePrice = createOHLCPrice(TEST_SYMBOL,
        requestWithoutUserId.getPurchaseDate(),
        new BigDecimal("180.00"), true);
      StockPriceDto currentPrice = createCurrentPrice(TEST_SYMBOL, new BigDecimal("230.00"));
      FxRateDto purchaseFxRate = new FxRateDto(
        requestWithoutUserId.getPurchaseDate(), new BigDecimal("1300.00"));
      FxRateDto currentFxRate = new FxRateDto(
        LocalDate.now(), new BigDecimal("1320.00"));

      SimulationResponse expectedResponse = createSimulationResponse();

      given(marketDataClientWrapper.getFxRate(anyString()))
        .willReturn(purchaseFxRate);
      given(marketDataClientWrapper.getCurrentPrice(TEST_SYMBOL))
        .willReturn(currentPrice);
      given(marketDataClientWrapper.getLatestFxRate())
        .willReturn(currentFxRate);
      given(marketDataClientWrapper.getDividendHistory(eq(TEST_SYMBOL), anyString(), anyString()))
        .willReturn(Collections.emptyList());
      given(marketDataClientWrapper.getOHLCPriceRange(eq(TEST_SYMBOL), anyString(), anyString()))
        .willReturn(List.of(purchasePrice));
      given(responseMapper.toSimulationResponse(
        any(SimulationRequest.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(LocalDate.class), any(BigDecimal.class),
        any(LocalDate.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        anyList()))
        .willReturn(expectedResponse);

      // when
      SimulationResponse result = tradingSimulationService.runSimulation(requestWithoutUserId);

      // then
      assertThat(result).isNotNull();
      // userId가 null이므로 히스토리 저장 안 함
      verify(backtestHistoryUtils, never()).saveBacktestHistory(any(), any(), any());
    }
  }

  @Nested
  @DisplayName("최적 타이밍 계산 테스트")
  class OptimalTimingTests {

    // responseMapper.toSimulationResponse 로 넘어가는 최적 타이밍 인자(매수일 · 매수가 · 매도일 · 매도가 · 수익률 순)
    private void runWith(List<OHLCPriceDto> range) {
      runWith(range, Map.of());
    }

    // 일자별 환율을 주지 않으면 모든 날이 1350
    private void runWith(List<OHLCPriceDto> range, Map<String, BigDecimal> fxRates) {
      // 매수는 가장 이른 날(응답 순서와 무관)
      OHLCPriceDto purchasePrice = range.stream().min(Comparator.comparing(OHLCPriceDto::date)).orElseThrow();
      if (!fxRates.isEmpty()) {
        given(marketDataClientWrapper.getBulkFxRates(anyList())).willReturn(fxRates);
      }

      given(marketDataClientWrapper.getOHLCPrice(eq(TEST_SYMBOL), anyString()))
        .willReturn(purchasePrice);
      given(marketDataClientWrapper.getFxRate(anyString()))
        .willReturn(new FxRateDto(testSimulationRequest.getPurchaseDate(),
          new BigDecimal("1300.00")));
      given(marketDataClientWrapper.getCurrentPrice(TEST_SYMBOL))
        .willReturn(createCurrentPrice(TEST_SYMBOL, new BigDecimal("230.00")));
      given(marketDataClientWrapper.getLatestFxRate())
        .willReturn(new FxRateDto(LocalDate.now(), new BigDecimal("1320.00")));
      given(marketDataClientWrapper.getDividendHistory(eq(TEST_SYMBOL), anyString(), anyString()))
        .willReturn(Collections.emptyList());
      given(marketDataClientWrapper.getOHLCPriceRange(eq(TEST_SYMBOL), anyString(), anyString()))
        .willReturn(range);
      given(responseMapper.toSimulationResponse(
        any(SimulationRequest.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(LocalDate.class), any(BigDecimal.class),
        any(LocalDate.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        anyList()))
        .willReturn(createSimulationResponse());

      tradingSimulationService.runSimulation(testSimulationRequest, false);
    }

    private OHLCPriceDto candle(String date, String close) {
      return createOHLCPrice(TEST_SYMBOL, LocalDate.parse(date), new BigDecimal(close), true);
    }

    @Test
    @DisplayName("매도일이 매수일보다 앞서는 답을 내지 않는다")
    void optimalTiming_NeverSellsBeforeBuying() {
      // given: 고점(7/28)이 먼저, 최저(8/12)가 뒤 — 따로 뽑으면 8월 매수 · 7월 매도
      runWith(List.of(
        candle("2026-07-02", "308.36"),
        candle("2026-07-28", "339.79"),
        candle("2026-08-12", "302.25"),
        candle("2026-08-26", "312.84")
      ));

      ArgumentCaptor<LocalDate> buyDate = ArgumentCaptor.forClass(LocalDate.class);
      ArgumentCaptor<LocalDate> sellDate = ArgumentCaptor.forClass(LocalDate.class);

      verify(responseMapper).toSimulationResponse(
        any(SimulationRequest.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), buyDate.capture(), any(BigDecimal.class),
        sellDate.capture(), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        anyList());

      // then: 7/2 에 사서 7/28 에 파는 것이 제약 아래 최대 수익
      assertThat(buyDate.getValue()).isEqualTo(LocalDate.parse("2026-07-02"));
      assertThat(sellDate.getValue()).isEqualTo(LocalDate.parse("2026-07-28"));
      assertThat(sellDate.getValue()).isAfterOrEqualTo(buyDate.getValue());
    }

    @Test
    @DisplayName("저점이 먼저 오면 저점 매수 고점 매도를 그대로 고른다")
    void optimalTiming_PicksLowThenHigh() {
      runWith(List.of(
        candle("2025-01-02", "248.62"),
        candle("2025-04-08", "171.37"),
        candle("2026-07-28", "339.79")
      ));

      ArgumentCaptor<LocalDate> buyDate = ArgumentCaptor.forClass(LocalDate.class);
      ArgumentCaptor<LocalDate> sellDate = ArgumentCaptor.forClass(LocalDate.class);

      verify(responseMapper).toSimulationResponse(
        any(SimulationRequest.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), buyDate.capture(), any(BigDecimal.class),
        sellDate.capture(), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        anyList());

      assertThat(buyDate.getValue()).isEqualTo(LocalDate.parse("2025-04-08"));
      assertThat(sellDate.getValue()).isEqualTo(LocalDate.parse("2026-07-28"));
    }

    @Test
    @DisplayName("내내 하락하면 매수일과 매도일이 같고 수익률이 0 이다")
    void optimalTiming_AllDownhill_ReturnsZero() {
      runWith(List.of(
        candle("2026-01-02", "300.00"),
        candle("2026-02-02", "250.00"),
        candle("2026-03-02", "200.00")
      ));

      ArgumentCaptor<LocalDate> buyDate = ArgumentCaptor.forClass(LocalDate.class);
      ArgumentCaptor<LocalDate> sellDate = ArgumentCaptor.forClass(LocalDate.class);
      ArgumentCaptor<BigDecimal> optimalReturn = ArgumentCaptor.forClass(BigDecimal.class);

      verify(responseMapper).toSimulationResponse(
        any(SimulationRequest.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), buyDate.capture(), any(BigDecimal.class),
        sellDate.capture(), any(BigDecimal.class), optimalReturn.capture(), any(BigDecimal.class),
        anyList());

      assertThat(buyDate.getValue()).isEqualTo(sellDate.getValue());
      assertThat(optimalReturn.getValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("원화 주가(종가 × 그 날짜 환율)로 가장 싸게 사서 가장 비싸게 파는 날을 고르고 수익률도 원화로 낸다")
    void optimalTiming_KrwPrice() {
      // 달러로는 1/5 최저 · 10/13 최고, 환율을 곱하면 1/9 가 더 싸고 10/28 이 더 비쌈
      runWith(List.of(
        candle("2023-01-05", "222.31"),
        candle("2023-01-09", "227.12"),
        candle("2025-10-28", "542.07"),
        candle("2026-10-13", "543.45")
      ), Map.of(
        "2023-01-05", new BigDecimal("1274.16"),
        "2023-01-09", new BigDecimal("1239.25"),
        "2025-10-28", new BigDecimal("1431.40"),
        "2026-10-13", new BigDecimal("1335.80")));

      ArgumentCaptor<LocalDate> buyDate = ArgumentCaptor.forClass(LocalDate.class);
      ArgumentCaptor<BigDecimal> buyPrice = ArgumentCaptor.forClass(BigDecimal.class);
      ArgumentCaptor<LocalDate> sellDate = ArgumentCaptor.forClass(LocalDate.class);
      ArgumentCaptor<BigDecimal> sellPrice = ArgumentCaptor.forClass(BigDecimal.class);
      ArgumentCaptor<BigDecimal> optimalReturn = ArgumentCaptor.forClass(BigDecimal.class);

      verify(responseMapper).toSimulationResponse(
        any(SimulationRequest.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), buyDate.capture(), buyPrice.capture(),
        sellDate.capture(), sellPrice.capture(), optimalReturn.capture(), any(BigDecimal.class),
        anyList());

      assertThat(buyDate.getValue()).isEqualTo(LocalDate.parse("2023-01-09"));
      assertThat(sellDate.getValue()).isEqualTo(LocalDate.parse("2025-10-28"));
      // 매수가 · 매도가는 그 날짜 달러 종가
      assertThat(buyPrice.getValue()).isEqualByComparingTo("227.12");
      assertThat(sellPrice.getValue()).isEqualByComparingTo("542.07");
      // 542.07 × 1431.40 / (227.12 × 1239.25) - 1
      assertThat(optimalReturn.getValue()).isEqualByComparingTo("175.68");
    }

    @Test
    @DisplayName("환율 기록 전 날은 첫 기록 환율로 원화 주가를 셈한다")
    void optimalTiming_FxBeforeFirstRecord() {
      // 1/5 는 환율 기록 전, 첫 기록 1000 으로 세면 1/5 가 가장 쌈
      runWith(List.of(
        candle("2023-01-05", "95.00"),
        candle("2023-01-09", "100.00"),
        candle("2023-03-01", "110.00")
      ), Map.of(
        "2023-01-09", new BigDecimal("1000"),
        "2023-03-01", new BigDecimal("1000")));

      ArgumentCaptor<LocalDate> buyDate = ArgumentCaptor.forClass(LocalDate.class);
      ArgumentCaptor<BigDecimal> optimalReturn = ArgumentCaptor.forClass(BigDecimal.class);
      verify(responseMapper).toSimulationResponse(
        any(SimulationRequest.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), buyDate.capture(), any(BigDecimal.class),
        any(LocalDate.class), any(BigDecimal.class), optimalReturn.capture(), any(BigDecimal.class),
        anyList());

      assertThat(buyDate.getValue()).isEqualTo(LocalDate.parse("2023-01-05"));
      // 110 / 95 - 1
      assertThat(optimalReturn.getValue()).isEqualByComparingTo("15.79");
    }

    @Test
    @DisplayName("응답이 날짜 순서로 오지 않아도 결과가 같다")
    void optimalTiming_UnsortedInput_SameResult() {
      runWith(List.of(
        candle("2026-08-12", "302.25"),
        candle("2026-07-02", "308.36"),
        candle("2026-08-26", "312.84"),
        candle("2026-07-28", "339.79")
      ));

      ArgumentCaptor<LocalDate> buyDate = ArgumentCaptor.forClass(LocalDate.class);
      ArgumentCaptor<LocalDate> sellDate = ArgumentCaptor.forClass(LocalDate.class);

      verify(responseMapper).toSimulationResponse(
        any(SimulationRequest.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        any(BigDecimal.class), any(BigDecimal.class), buyDate.capture(), any(BigDecimal.class),
        sellDate.capture(), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class),
        anyList());

      assertThat(buyDate.getValue()).isEqualTo(LocalDate.parse("2026-07-02"));
      assertThat(sellDate.getValue()).isEqualTo(LocalDate.parse("2026-07-28"));
    }
  }

  @Nested
  @DisplayName("매도일 평가")
  class SaleDateTests {

    private final LocalDate purchaseDate = LocalDate.of(2024, 1, 15);
    private final LocalDate saleDate = LocalDate.of(2024, 6, 14);
    private TradingSimulationServiceImpl service;

    @BeforeEach
    void setUpService() {
      service = new TradingSimulationServiceImpl(
        marketDataClientWrapper, new ResponseMapper(), backtestHistoryUtils);
    }

    private SimulationRequest request(LocalDate sale, boolean reinvest) {
      return SimulationRequest.builder()
        .symbol(TEST_SYMBOL)
        .purchaseDate(purchaseDate)
        .saleDate(sale)
        .investmentAmount(new BigDecimal("1300000"))
        .reinvestDividends(reinvest)
        .build();
    }

    private void stubPurchase() {
      given(marketDataClientWrapper.getOHLCPrice(TEST_SYMBOL, "2024-01-15"))
        .willReturn(createOHLCPrice(TEST_SYMBOL, purchaseDate, new BigDecimal("100.00"), true));
      given(marketDataClientWrapper.getFxRate("2024-01-15"))
        .willReturn(new FxRateDto(purchaseDate, new BigDecimal("1300")));
    }

    private void stubSale() {
      given(marketDataClientWrapper.getOHLCPrice(TEST_SYMBOL, "2024-06-14"))
        .willReturn(createOHLCPrice(TEST_SYMBOL, saleDate, new BigDecimal("150.00"), true));
      given(marketDataClientWrapper.getFxRate("2024-06-14"))
        .willReturn(new FxRateDto(saleDate, new BigDecimal("1350")));
    }

    private DividendDto dividend(LocalDate exDate) {
      return new DividendDto(TEST_SYMBOL, exDate, exDate.plusDays(5), null,
        new BigDecimal("1.00"), "USD", "test");
    }

    @Test
    @DisplayName("과거 매도일이면 그 날짜 조정 종가 · 환율로 평가하고 평가일을 그 날짜로 둔다")
    void pastSaleDate_ValuesAtSaleDate() {
      stubPurchase();
      stubSale();
      given(marketDataClientWrapper.getDividendHistory(TEST_SYMBOL, "2024-01-15", "2024-06-14"))
        .willReturn(Collections.emptyList());
      given(marketDataClientWrapper.getOHLCPriceRange(TEST_SYMBOL, "2024-01-10", "2024-06-14"))
        .willReturn(List.of());

      SimulationResponse response = service.runSimulation(request(saleDate, false), false);

      assertThat(response.getCurrentDate()).isEqualTo(saleDate);
      assertThat(response.getCurrentPrice()).isEqualByComparingTo("150.00");
      assertThat(response.getCurrentFxRate()).isEqualByComparingTo("1350");
      verify(marketDataClientWrapper, never()).getCurrentPrice(any());
      verify(marketDataClientWrapper, never()).getLatestFxRate();
    }

    @Test
    @DisplayName("매도일이 없으면 현재가 · 최신 환율로 오늘 평가한다")
    void noSaleDate_ValuesToday() {
      String today = LocalDate.now().toString();
      stubPurchase();
      given(marketDataClientWrapper.getCurrentPrice(TEST_SYMBOL))
        .willReturn(createCurrentPrice(TEST_SYMBOL, new BigDecimal("230.00")));
      given(marketDataClientWrapper.getLatestFxRate())
        .willReturn(new FxRateDto(LocalDate.now(), new BigDecimal("1320")));
      given(marketDataClientWrapper.getDividendHistory(TEST_SYMBOL, "2024-01-15", today))
        .willReturn(Collections.emptyList());
      given(marketDataClientWrapper.getOHLCPriceRange(TEST_SYMBOL, "2024-01-10", today))
        .willReturn(List.of());

      SimulationResponse response = service.runSimulation(request(null, false), false);

      assertThat(response.getCurrentDate()).isEqualTo(LocalDate.now());
      assertThat(response.getCurrentPrice()).isEqualByComparingTo("230.00");
      assertThat(response.getCurrentFxRate()).isEqualByComparingTo("1320");
    }

    @Test
    @DisplayName("매도일이 매수일보다 앞서면 시세를 부르기 전에 막는다")
    void saleDateBeforePurchase_Throws() {
      assertThatThrownBy(() ->
        service.runSimulation(request(LocalDate.of(2024, 1, 14), false), false))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.STRATEGY_DATE_RANGE_INVALID);
      verify(marketDataClientWrapper, never()).getOHLCPrice(any(), any());
    }

    @Test
    @DisplayName("배당 재투자는 매도일까지의 배당만 쓴다")
    void reinvest_OnlyUntilSaleDate() {
      stubPurchase();
      stubSale();
      given(marketDataClientWrapper.getDividendHistory(TEST_SYMBOL, "2024-01-15", "2024-06-14"))
        .willReturn(List.of(dividend(LocalDate.of(2024, 5, 10)), dividend(LocalDate.of(2024, 8, 9))));
      given(marketDataClientWrapper.getOHLCPriceRange(eq(TEST_SYMBOL), anyString(), anyString()))
        .willReturn(List.of(createOHLCPrice(TEST_SYMBOL, LocalDate.of(2024, 5, 10),
          new BigDecimal("120.00"), true)));

      SimulationResponse response = service.runSimulation(request(saleDate, true), false);

      assertThat(response.getDividendReinvestDates()).containsExactly(LocalDate.of(2024, 5, 10));
    }

    @Test
    @DisplayName("매수가 · 평가가는 매수일 5일 전부터 받은 기간 일봉에서 찾고 날짜별 시세를 부르지 않는다")
    void purchaseAndSale_FromOneRange() {
      given(marketDataClientWrapper.getOHLCPriceRange(eq(TEST_SYMBOL), anyString(), anyString()))
        .willReturn(List.of(
          createOHLCPrice(TEST_SYMBOL, purchaseDate, new BigDecimal("100.00"), true),
          createOHLCPrice(TEST_SYMBOL, saleDate, new BigDecimal("150.00"), true)));
      given(marketDataClientWrapper.getFxRate("2024-01-15"))
        .willReturn(new FxRateDto(purchaseDate, new BigDecimal("1300")));
      given(marketDataClientWrapper.getFxRate("2024-06-14"))
        .willReturn(new FxRateDto(saleDate, new BigDecimal("1350")));
      given(marketDataClientWrapper.getDividendHistory(TEST_SYMBOL, "2024-01-15", "2024-06-14"))
        .willReturn(Collections.emptyList());

      SimulationResponse response = service.runSimulation(request(saleDate, false), false);

      assertThat(response.getPurchasePrice()).isEqualByComparingTo("100.00");
      assertThat(response.getCurrentPrice()).isEqualByComparingTo("150.00");
      verify(marketDataClientWrapper).getOHLCPriceRange(TEST_SYMBOL, "2024-01-10", "2024-06-14");
      verify(marketDataClientWrapper, never()).getOHLCPrice(any(), any());
    }

    @Test
    @DisplayName("배당일 주가 · 최적 타이밍도 같은 기간 일봉을 써서 기간 시세 조회는 한 번이다")
    void dividendAndOptimalTiming_ReuseRange() {
      given(marketDataClientWrapper.getOHLCPriceRange(eq(TEST_SYMBOL), anyString(), anyString()))
        .willReturn(List.of(
          createOHLCPrice(TEST_SYMBOL, purchaseDate, new BigDecimal("100.00"), true),
          createOHLCPrice(TEST_SYMBOL, LocalDate.of(2024, 5, 10), new BigDecimal("120.00"), true),
          createOHLCPrice(TEST_SYMBOL, saleDate, new BigDecimal("150.00"), true)));
      given(marketDataClientWrapper.getFxRate("2024-01-15"))
        .willReturn(new FxRateDto(purchaseDate, new BigDecimal("1300")));
      given(marketDataClientWrapper.getFxRate("2024-06-14"))
        .willReturn(new FxRateDto(saleDate, new BigDecimal("1350")));
      given(marketDataClientWrapper.getDividendHistory(TEST_SYMBOL, "2024-01-15", "2024-06-14"))
        .willReturn(List.of(dividend(LocalDate.of(2024, 5, 10))));

      SimulationResponse response = service.runSimulation(request(saleDate, true), false);

      assertThat(response.getDividendReinvestDates()).containsExactly(LocalDate.of(2024, 5, 10));
      assertThat(response.getOptimalBuyDate()).isEqualTo(purchaseDate);
      assertThat(response.getOptimalSellDate()).isEqualTo(saleDate);
      verify(marketDataClientWrapper, times(1)).getOHLCPriceRange(eq(TEST_SYMBOL), anyString(), anyString());
      verify(marketDataClientWrapper, never()).getOHLCPrice(any(), any());
    }
  }


  // === Helper Methods ===

  private OHLCPriceDto createOHLCPrice(String symbol, LocalDate date, BigDecimal closePrice,
                                       boolean available) {
    return new OHLCPriceDto(
      symbol,
      date,
      closePrice.subtract(new BigDecimal("5.00")),  // openPrice
      closePrice.add(new BigDecimal("10.00")),      // highPrice
      closePrice.subtract(new BigDecimal("8.00")),  // lowPrice
      closePrice,                                    // closePrice
      null,                                          // adjustedClose
      1000000L,                                      // volume
      "USD",                                         // currency
      available                                      // available
    );
  }

  private StockPriceDto createCurrentPrice(String symbol, BigDecimal currentPrice) {
    return new StockPriceDto(
      symbol,                                          // symbol
      "Test Stock",                                    // name
      currentPrice,                                    // currentPrice
      currentPrice.subtract(new BigDecimal("2.00")),  // previousClose
      new BigDecimal("2.00"),                          // change
      new BigDecimal("0.87"),                          // changePercent
      null,                                            // volume
      null,                                            // date
      LocalDateTime.now(),                             // lastUpdated
      null,                                            // openPrice
      null,                                            // highPrice
      null,                                            // lowPrice
      null,                                            // closePrice
      null,                                            // adjustedClose
      "USD",                                           // currency
      true,                                            // available
      null,                                            // assetType
      null                                             // marketCap
    );
  }

  private SimulationResponse createSimulationResponse() {
    return SimulationResponse.builder()
      .symbol(TEST_SYMBOL)
      .purchaseDate(LocalDate.of(2024, 1, 15))
      .currentDate(LocalDate.now())
      .investmentAmount(new BigDecimal("1000000"))
      .purchasePrice(new BigDecimal("180.00"))
      .shares(new BigDecimal("41.7"))
      .currentPrice(new BigDecimal("230.00"))
      .currentValue(new BigDecimal("9591.00"))
      .stockReturn(new BigDecimal("2085.00"))
      .stockReturnPercent(new BigDecimal("27.78"))
      .purchaseFxRate(new BigDecimal("1300.00"))
      .currentFxRate(new BigDecimal("1320.00"))
      .fxReturn(new BigDecimal("20.00"))
      .fxReturnPercent(new BigDecimal("1.54"))
      .totalDividends(BigDecimal.ZERO)
      .dividendYield(BigDecimal.ZERO)
      .tradingFee(new BigDecimal("18.82"))
      .remainingCash(new BigDecimal("498.17"))
      .totalReturnPercent(new BigDecimal("29.32"))
      .currentValueKrw(new BigDecimal("12660120"))
      .remainingCashKrw(new BigDecimal("657588"))
      .totalAssetKrw(new BigDecimal("13317708"))
      .totalReturnKrw(new BigDecimal("3317708"))
      .build();
  }

  @Nested
  @DisplayName("휴장일 매수")
  class HolidayPurchaseTests {

    private TradingSimulationServiceImpl service;

    @BeforeEach
    void setUpService() {
      service = new TradingSimulationServiceImpl(marketDataClientWrapper, new ResponseMapper(), backtestHistoryUtils);
    }

    private void stubPrice(String date, String close) {
      given(marketDataClientWrapper.getOHLCPrice(TEST_SYMBOL, date))
        .willReturn(createOHLCPrice(TEST_SYMBOL, LocalDate.parse(date), new BigDecimal(close), true));
    }

    private void stubClosed(String date) {
      given(marketDataClientWrapper.getOHLCPrice(TEST_SYMBOL, date))
        .willReturn(createOHLCPrice(TEST_SYMBOL, LocalDate.parse(date), BigDecimal.TEN, false));
    }

    private void stubFx(String date, String rate) {
      given(marketDataClientWrapper.getFxRate(date)).willReturn(new FxRateDto(LocalDate.parse(date), new BigDecimal(rate)));
    }

    private SimulationResponse run(String purchaseDate, String saleDate, String amount) {
      return service.runSimulation(SimulationRequest.builder().symbol(TEST_SYMBOL)
        .purchaseDate(LocalDate.parse(purchaseDate)).saleDate(LocalDate.parse(saleDate))
        .investmentAmount(new BigDecimal(amount)).build(), false);
    }

    @Test
    @DisplayName("휴장일에 사면 다음 거래일 종가 · 환율로 사고, 응답의 매수일도 그 날짜다")
    void buysOnNextTradingDay() {
      // 1/1(일) · 1/2(휴장) 다음 거래일 1/3, 그 전 거래일 12/30 시세 · 환율은 안 씀
      stubClosed("2023-01-01");
      stubClosed("2023-01-02");
      stubPrice("2023-01-03", "125.00");
      stubFx("2023-01-03", "1250");
      lenient().when(marketDataClientWrapper.getOHLCPrice(TEST_SYMBOL, "2022-12-30"))
        .thenReturn(createOHLCPrice(TEST_SYMBOL, LocalDate.of(2022, 12, 30), new BigDecimal("129.93"), true));
      lenient().when(marketDataClientWrapper.getFxRate("2023-01-01"))
        .thenReturn(new FxRateDto(LocalDate.of(2023, 1, 1), new BigDecimal("1261.87")));
      stubPrice("2023-06-30", "190.00");
      stubFx("2023-06-30", "1300");

      SimulationResponse response = run("2023-01-01", "2023-06-30", "1250000");

      // 125만원 ÷ 1250 = 1000달러 → 125달러에 8주
      assertThat(response.getPurchasePrice()).isEqualByComparingTo("125.00");
      assertThat(response.getPurchaseFxRate()).isEqualByComparingTo("1250");
      assertThat(response.getShares()).isEqualByComparingTo("8");
      assertThat(response.getPurchaseDate()).isEqualTo(LocalDate.of(2023, 1, 3));
    }

    @Test
    @DisplayName("평가일까지 다음 거래일이 없으면 그 전 거래일 종가로 산다")
    void buysOnPreviousTradingDayWhenNoNextBeforeValuation() {
      // 6/3(토)에 사서 6/4(일)에 평가, 그 사이 거래일이 없어 6/2(금) 종가
      stubClosed("2023-06-03");
      stubClosed("2023-06-04");
      stubPrice("2023-06-02", "180.00");
      stubFx("2023-06-02", "1300");
      stubFx("2023-06-04", "1300");
      lenient().when(marketDataClientWrapper.getFxRate("2023-06-03"))
        .thenReturn(new FxRateDto(LocalDate.of(2023, 6, 3), new BigDecimal("1300")));

      SimulationResponse response = run("2023-06-03", "2023-06-04", "1300000");

      assertThat(response.getPurchasePrice()).isEqualByComparingTo("180.00");
      assertThat(response.getPurchaseDate()).isEqualTo(LocalDate.of(2023, 6, 2));
    }
  }

  @Nested
  @DisplayName("배당 재투자 세부 · 최적 시점 실패")
  class ReinvestDetailTests {

    private final LocalDate purchaseDate = LocalDate.of(2024, 1, 15);
    private final LocalDate saleDate = LocalDate.of(2024, 6, 14);
    private final LocalDate exDate = LocalDate.of(2024, 5, 10);
    private TradingSimulationServiceImpl service;

    @BeforeEach
    void setUpService() {
      service = new TradingSimulationServiceImpl(marketDataClientWrapper, new ResponseMapper(), backtestHistoryUtils);
    }

    // 130만원 · 환율 1300 · 매수가 100 → 10주, 매도일 평가가 150
    private SimulationRequest request(boolean reinvest, String taxRate) {
      return SimulationRequest.builder().symbol(TEST_SYMBOL).purchaseDate(purchaseDate).saleDate(saleDate)
        .investmentAmount(new BigDecimal("1300000")).reinvestDividends(reinvest)
        .dividendTaxRate(new BigDecimal(taxRate)).build();
    }

    private void stubPurchaseAndSale() {
      given(marketDataClientWrapper.getOHLCPrice(TEST_SYMBOL, "2024-01-15"))
        .willReturn(createOHLCPrice(TEST_SYMBOL, purchaseDate, new BigDecimal("100.00"), true));
      given(marketDataClientWrapper.getFxRate("2024-01-15")).willReturn(new FxRateDto(purchaseDate, new BigDecimal("1300")));
      given(marketDataClientWrapper.getOHLCPrice(TEST_SYMBOL, "2024-06-14"))
        .willReturn(createOHLCPrice(TEST_SYMBOL, saleDate, new BigDecimal("150.00"), true));
      given(marketDataClientWrapper.getFxRate("2024-06-14")).willReturn(new FxRateDto(saleDate, new BigDecimal("1300")));
    }

    private void stubDividend(String perShare) {
      given(marketDataClientWrapper.getDividendHistory(TEST_SYMBOL, "2024-01-15", "2024-06-14"))
        .willReturn(List.of(new DividendDto(TEST_SYMBOL, exDate, exDate.plusDays(5), null, new BigDecimal(perShare), "USD", "test")));
    }

    @Test
    @DisplayName("원천징수 세율이 있으면 세후 배당으로만 다시 산다")
    void reinvestsAfterTax() {
      stubPurchaseAndSale();
      stubDividend("1.00");
      given(marketDataClientWrapper.getOHLCPriceRange(eq(TEST_SYMBOL), anyString(), anyString()))
        .willReturn(List.of(createOHLCPrice(TEST_SYMBOL, exDate, new BigDecimal("100.00"), true)));

      SimulationResponse response = service.runSimulation(request(true, "0.15"), false);

      // 배당 10달러 - 세금 1.5 = 8.5 달러 → 100 달러에 0.085주
      assertThat(response.getDividendsReinvested()).isEqualByComparingTo("8.50");
      assertThat(response.getShares()).isEqualByComparingTo("10.085");
      // 배당은 주식 수에만 들어가고 현금으로 또 더하지 않는다: 10.085주 × 150달러 × 1300원
      assertThat(response.getTotalAssetKrw()).isEqualByComparingTo("1966575");
      // 총 배당금은 세전 10달러(재투자로 늘어난 10.085주로 다시 곱하지 않음)
      assertThat(response.getTotalDividends()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("재투자를 켜면 총 배당금은 배당마다 그때 주식 수로 받은 합이다")
    void totalDividendsUseSharesAtEachExDate() {
      LocalDate firstExDate = LocalDate.of(2024, 3, 8);
      stubPurchaseAndSale();
      given(marketDataClientWrapper.getDividendHistory(TEST_SYMBOL, "2024-01-15", "2024-06-14")).willReturn(List.of(
        new DividendDto(TEST_SYMBOL, firstExDate, firstExDate.plusDays(5), null, new BigDecimal("1.00"), "USD", "test"),
        new DividendDto(TEST_SYMBOL, exDate, exDate.plusDays(5), null, new BigDecimal("1.00"), "USD", "test")));
      given(marketDataClientWrapper.getOHLCPriceRange(eq(TEST_SYMBOL), anyString(), anyString())).willReturn(List.of(
        createOHLCPrice(TEST_SYMBOL, firstExDate, new BigDecimal("100.00"), true),
        createOHLCPrice(TEST_SYMBOL, exDate, new BigDecimal("100.00"), true)));

      SimulationResponse response = service.runSimulation(request(true, "0"), false);

      // 10주에 1달러 → 0.1주 추가, 다음 배당은 10.1주에 1달러(마지막 10.201주로 두 번 곱하지 않음)
      assertThat(response.getShares()).isEqualByComparingTo("10.201");
      assertThat(response.getTotalDividends()).isEqualByComparingTo("20.10");
    }

    @Test
    @DisplayName("재투자를 끄면 세후 배당을 원화로 바꿔 총자산에 더한다")
    void cashDividendAddedToTotalAsset() {
      stubPurchaseAndSale();
      stubDividend("1.00");
      given(marketDataClientWrapper.getOHLCPriceRange(eq(TEST_SYMBOL), anyString(), anyString())).willReturn(List.of());

      SimulationResponse noTax = service.runSimulation(request(false, "0"), false);
      SimulationResponse taxed = service.runSimulation(request(false, "0.15"), false);

      // 10주 × 150달러 × 1300원 = 1,950,000원 + 배당 10달러(세후 8.5달러) × 1300원
      assertThat(noTax.getShares()).isEqualByComparingTo("10");
      assertThat(noTax.getTotalAssetKrw()).isEqualByComparingTo("1963000");
      assertThat(taxed.getTotalAssetKrw()).isEqualByComparingTo("1961050");
    }

    @Test
    @DisplayName("배당일 시세가 일괄 조회에 없으면 그 날짜 시세를 따로 조회해 산다")
    void fetchesMissingDividendDayPrice() {
      stubPurchaseAndSale();
      stubDividend("1.00");
      given(marketDataClientWrapper.getOHLCPriceRange(eq(TEST_SYMBOL), anyString(), anyString())).willReturn(List.of());
      given(marketDataClientWrapper.getOHLCPrice(TEST_SYMBOL, "2024-05-10"))
        .willReturn(createOHLCPrice(TEST_SYMBOL, exDate, new BigDecimal("125.00"), true));

      SimulationResponse response = service.runSimulation(request(true, "0"), false);

      // 10달러 / 125 = 0.08주
      assertThat(response.getShares()).isEqualByComparingTo("10.08");
      assertThat(response.getDividendReinvestDates()).containsExactly(exDate);
    }

    @Test
    @DisplayName("주당 배당이 0 이면 재투자하지 않는다")
    void skipsZeroDividend() {
      stubPurchaseAndSale();
      stubDividend("0");
      given(marketDataClientWrapper.getOHLCPriceRange(eq(TEST_SYMBOL), anyString(), anyString())).willReturn(List.of());

      SimulationResponse response = service.runSimulation(request(true, "0"), false);

      assertThat(response.getDividendReinvestDates()).isEmpty();
      assertThat(response.getShares()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("최적 시점 계산이 실패해도 시뮬레이션 결과는 나오고 최적 시점만 빈다")
    void optimalTimingFailureLeavesItEmpty() {
      stubPurchaseAndSale();
      given(marketDataClientWrapper.getDividendHistory(TEST_SYMBOL, "2024-01-15", "2024-06-14")).willReturn(List.of());
      given(marketDataClientWrapper.getOHLCPriceRange(eq(TEST_SYMBOL), anyString(), anyString()))
        .willThrow(new RuntimeException("range down"));

      SimulationResponse response = service.runSimulation(request(false, "0"), false);

      assertThat(response.getCurrentPrice()).isEqualByComparingTo("150.00");
      assertThat(response.getOptimalBuyDate()).isNull();
      assertThat(response.getOptimalReturnPercent()).isNull();
    }

    @Test
    @DisplayName("재투자를 켜도 배당이 없거나 권리락일 없는 배당뿐이면 주식 수가 그대로다")
    void reinvestWithoutUsableDividends() {
      stubPurchaseAndSale();
      given(marketDataClientWrapper.getDividendHistory(TEST_SYMBOL, "2024-01-15", "2024-06-14"))
        .willReturn(List.of())
        .willReturn(List.of(new DividendDto(TEST_SYMBOL, null, null, null, BigDecimal.ONE, "USD", "test")));
      given(marketDataClientWrapper.getOHLCPriceRange(eq(TEST_SYMBOL), anyString(), anyString())).willReturn(List.of());

      SimulationResponse noDividends = service.runSimulation(request(true, "0"), false);
      SimulationResponse noExDate = service.runSimulation(request(true, "0"), false);

      assertThat(noDividends.getShares()).isEqualByComparingTo("10");
      assertThat(noExDate.getShares()).isEqualByComparingTo("10");
      assertThat(noExDate.getDividendReinvestDates()).isEmpty();
    }
  }

  @Nested
  @DisplayName("일별 평가 가치 · 지표")
  class DailyValueTests {

    private final LocalDate purchaseDate = LocalDate.of(2024, 1, 15);
    private final LocalDate march = LocalDate.of(2024, 3, 1);
    private final LocalDate april = LocalDate.of(2024, 4, 1);
    private final LocalDate exDate = LocalDate.of(2024, 5, 10);
    private final LocalDate saleDate = LocalDate.of(2024, 6, 14);
    private TradingSimulationServiceImpl service;

    @BeforeEach
    void setUpService() {
      service = new TradingSimulationServiceImpl(marketDataClientWrapper, new ResponseMapper(), backtestHistoryUtils);
      // 수동 환율 테스트는 시장 환율을 부르지 않음
      lenient().when(marketDataClientWrapper.getFxRate("2024-01-15")).thenReturn(new FxRateDto(purchaseDate, new BigDecimal("1300")));
      lenient().when(marketDataClientWrapper.getFxRate("2024-06-14")).thenReturn(new FxRateDto(saleDate, new BigDecimal("1300")));
    }

    // 130만원 · 환율 1300 · 매수가 100 → 10주(수수료 없음), 매도일 평가가 150
    private SimulationRequest request(boolean reinvest, String taxRate) {
      return SimulationRequest.builder().symbol(TEST_SYMBOL).purchaseDate(purchaseDate).saleDate(saleDate)
        .investmentAmount(new BigDecimal("1300000")).reinvestDividends(reinvest)
        .dividendTaxRate(new BigDecimal(taxRate)).build();
    }

    private OHLCPriceDto candle(LocalDate date, String close) {
      return createOHLCPrice(TEST_SYMBOL, date, new BigDecimal(close), true);
    }

    private void stubRange(OHLCPriceDto... candles) {
      given(marketDataClientWrapper.getOHLCPriceRange(eq(TEST_SYMBOL), anyString(), anyString()))
        .willReturn(List.of(candles));
    }

    // 3월 120달러, 4월 60달러(환율 1400), 매도일 150달러
    private void stubSwing() {
      stubRange(candle(purchaseDate, "100.00"), candle(march, "120.00"), candle(april, "60.00"),
        candle(saleDate, "150.00"));
      given(marketDataClientWrapper.getBulkFxRates(anyList())).willReturn(Map.of(
        "2024-01-15", new BigDecimal("1300"), "2024-03-01", new BigDecimal("1300"),
        "2024-04-01", new BigDecimal("1400"), "2024-06-14", new BigDecimal("1300")));
    }

    private void stubDividend() {
      given(marketDataClientWrapper.getDividendHistory(TEST_SYMBOL, "2024-01-15", "2024-06-14"))
        .willReturn(List.of(new DividendDto(TEST_SYMBOL, exDate, exDate.plusDays(5), null, new BigDecimal("1.00"),
          "USD", "test")));
      stubRange(candle(purchaseDate, "100.00"), candle(exDate, "100.00"), candle(saleDate, "150.00"));
    }

    private BigDecimal valueUsdOn(SimulationResponse response, LocalDate date) {
      return response.getDailyValues().stream().filter(v -> v.date().equals(date)).findFirst()
        .map(DailyValue::valueUsd).orElseThrow();
    }

    @Test
    @DisplayName("날마다 주식 수 × 종가를 그 날짜 환율로 바꾼 값을 싣고, 평가일은 총자산이다")
    void dailyValuesFollowCloseAndFx() {
      stubSwing();

      SimulationResponse response = service.runSimulation(request(false, "0"), false);

      assertThat(response.getDailyValues()).extracting(DailyValue::date)
        .containsExactly(purchaseDate, march, april, saleDate);
      assertThat(response.getDailyValues()).extracting(DailyValue::valueUsd)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("1000"), new BigDecimal("1200"), new BigDecimal("600"), new BigDecimal("1500"));
      // 4월은 600달러 × 1400원, 매도일은 총자산 10주 × 150달러 × 1300원
      assertThat(response.getDailyValues()).extracting(DailyValue::valueKrw)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("1300000"), new BigDecimal("1560000"), new BigDecimal("840000"),
          new BigDecimal("1950000"));
    }

    @Test
    @DisplayName("양도세를 켜도 팔지 않으면 연도 없이 세금 0 이고 세후 평가 · 수익률은 총 자산 · 총 수익률이며, 비우면 싣지 않는다")
    void capitalGainsTaxWithoutSale() {
      stubSwing();

      SimulationResponse response = service.runSimulation(SimulationRequest.builder().symbol(TEST_SYMBOL)
        .purchaseDate(purchaseDate).saleDate(saleDate).investmentAmount(new BigDecimal("1300000"))
        .capitalGainsTax(true).build(), false);

      assertThat(response.getCapitalGainsTax().years()).isEmpty();
      assertThat(response.getCapitalGainsTax().totalTaxKrw()).isEqualByComparingTo("0");
      assertThat(response.getCapitalGainsTax().stockKrw()).isEqualByComparingTo(response.getCurrentValueKrw());
      assertThat(response.getCapitalGainsTax().afterTaxValueKrw()).isEqualByComparingTo(response.getTotalAssetKrw());
      assertThat(response.getCapitalGainsTax().afterTaxReturnPercent())
        .isEqualByComparingTo(response.getTotalReturnPercent());
      assertThat(service.runSimulation(request(false, "0"), false).getCapitalGainsTax()).isNull();
    }

    @Test
    @DisplayName("기간 끝 매도를 고르면 마지막 거래일 종가에 수수료를 빼고 전량 팔아 주식 0 · 판 돈 현금이고, 판 해의 양도세는 납부일이 기간 뒤라 남은 양도세로 뺀다")
    void sellAtEnd() {
      // 일요일 평가라 마지막 거래일은 6월 14일
      LocalDate sunday = LocalDate.of(2024, 6, 16);
      given(marketDataClientWrapper.getFxRate("2024-06-16")).willReturn(new FxRateDto(sunday, new BigDecimal("1300")));
      stubSwing();

      SimulationResponse response = service.runSimulation(SimulationRequest.builder().symbol(TEST_SYMBOL)
        .purchaseDate(purchaseDate).saleDate(sunday).investmentAmount(new BigDecimal("13000000"))
        .tradingFeeRate(new BigDecimal("0.01")).periodEnd(PeriodEnd.SELL_AT_END).capitalGainsTax(true).build(), false);

      // 1만 달러에서 수수료 100달러를 빼고 99주, 99주 × 150달러 - 수수료 148.50달러
      assertThat(response.getShares()).isEqualByComparingTo("99");
      assertThat(response.getCurrentValue()).isEqualByComparingTo("0");
      assertThat(response.getCurrentValueKrw()).isEqualByComparingTo("0");
      assertThat(response.getRemainingCash()).isEqualByComparingTo("14701.50");
      assertThat(response.getTradingFee()).isEqualByComparingTo("248.50");
      assertThat(response.getTotalAssetKrw()).isEqualByComparingTo("19111950");
      assertThat(valueUsdOn(response, saleDate)).isEqualByComparingTo("14701.50");
      // 1911만 1,950원 - 1만 달러 × 1300원, 250만원 공제 뒤 22%
      assertThat(response.getCapitalGainsTax().years()).containsExactly(
        new CapitalGainsTax.Year(2024, new BigDecimal("6111950"), new BigDecimal("2500000"), new BigDecimal("794629"),
          null, BigDecimal.ZERO));
      assertThat(response.getCapitalGainsTax().paidTaxKrw()).isEqualByComparingTo("0");
      assertThat(response.getCapitalGainsTax().unpaidTaxKrw()).isEqualByComparingTo("794629");
      assertThat(response.getCapitalGainsTax().cashKrw()).isEqualByComparingTo("19111950");
      assertThat(response.getCapitalGainsTax().stockKrw()).isEqualByComparingTo("0");
      assertThat(response.getCapitalGainsTax().afterTaxValueKrw()).isEqualByComparingTo("18317321");
      // 판 날 뒤 배당은 받지 않음
      verify(marketDataClientWrapper).getDividendHistory(TEST_SYMBOL, "2024-01-15", "2024-06-14");
    }

    @Test
    @DisplayName("재투자를 켜고 기간 끝에 팔면 재투자로 늘어난 주식까지 팔고 그 주식의 원가는 배당락일 환율로 센다")
    void reinvestThenSellAtEnd() {
      stubDividend();

      SimulationResponse response = service.runSimulation(SimulationRequest.builder().symbol(TEST_SYMBOL)
        .purchaseDate(purchaseDate).saleDate(saleDate).investmentAmount(new BigDecimal("1300000"))
        .reinvestDividends(true).periodEnd(PeriodEnd.SELL_AT_END).capitalGainsTax(true).build(), false);

      // 10주 + 배당 10달러로 0.1주, 10.1주 × 150달러 × 1300원 - (130만원 + 10달러 × 1350원)
      assertThat(response.getRemainingCash()).isEqualByComparingTo("1515.00");
      assertThat(response.getCapitalGainsTax().years()).singleElement()
        .satisfies(year -> assertThat(year.realizedGainKrw()).isEqualByComparingTo("656000"));
    }

    @Test
    @DisplayName("원화 · 달러 지표를 따로 내고, 환율이 오른 날은 원화 낙폭이 더 작다")
    void metricsPerCurrency() {
      stubSwing();

      SimulationResponse response = service.runSimulation(request(false, "0"), false);

      // 달러 1200 → 600 은 -50%, 원화 156만 → 84만 은 -46.15%
      assertThat(response.getMetricsUsd().mdd()).isEqualByComparingTo("-50.00");
      assertThat(response.getMetricsKrw().mdd()).isEqualByComparingTo("-46.15");
      assertThat(response.getMetricsKrw().mddPeakDate()).isEqualTo(march);
      assertThat(response.getMetricsKrw().mddTroughDate()).isEqualTo(april);
      assertThat(response.getMetricsKrw().mddRecoveryDate()).isEqualTo(saleDate);
      // 1년이 안 되어 연평균 수익률은 없음
      assertThat(response.getMetricsKrw().cagr()).isNull();
      assertThat(response.getMetricsKrw().volatility()).isNotNull();
      // 4월의 날마다 낙폭
      DailyValue aprilValue = response.getDailyValues().get(2);
      assertThat(aprilValue.drawdownUsd()).isEqualByComparingTo("-50.00");
      assertThat(aprilValue.drawdownKrw()).isEqualByComparingTo("-46.15");
    }

    @Test
    @DisplayName("배당을 다시 사면 배당락일부터 늘어난 주식 수로 센다")
    void reinvestedSharesFromExDate() {
      stubDividend();

      SimulationResponse response = service.runSimulation(request(true, "0"), false);

      // 10주에 1달러 → 0.1주, 배당락일 10.1주 × 100달러
      assertThat(valueUsdOn(response, purchaseDate)).isEqualByComparingTo("1000");
      assertThat(valueUsdOn(response, exDate)).isEqualByComparingTo("1010");
    }

    @Test
    @DisplayName("재투자를 끄면 세후 배당이 배당락일부터 현금으로 더해진다")
    void cashDividendFromExDate() {
      stubDividend();

      SimulationResponse response = service.runSimulation(request(false, "0.15"), false);

      // 10주 × 100달러 + 배당 10달러의 세후 8.5달러
      assertThat(valueUsdOn(response, purchaseDate)).isEqualByComparingTo("1000");
      assertThat(valueUsdOn(response, exDate)).isEqualByComparingTo("1008.50");
    }

    @Test
    @DisplayName("기간 환율 기록이 없으면 최적 시점은 기본 환율로 내고 일별 원화 값은 매수 · 평가일 환율을 날짜 비례로 잇는다")
    void bulkFxEmpty() {
      stubRange(candle(purchaseDate, "100.00"), candle(march, "120.00"), candle(saleDate, "150.00"));
      given(marketDataClientWrapper.getFxRate("2024-06-14")).willReturn(new FxRateDto(saleDate, new BigDecimal("1400")));
      given(marketDataClientWrapper.getBulkFxRates(anyList())).willReturn(Map.of());

      SimulationResponse response = service.runSimulation(request(false, "0"), false);

      // 기본 환율 하나로 100달러에 사서 150달러에 팜
      assertThat(response.getOptimalBuyDate()).isEqualTo(purchaseDate);
      assertThat(response.getOptimalSellDate()).isEqualTo(saleDate);
      // 1200달러 × (1300 + 100 × 46/151)
      assertThat(response.getDailyValues().get(1).valueKrw()).isEqualByComparingTo("1596556");
    }

    @Test
    @DisplayName("기간 환율 조회가 예외를 내도 일별 원화 값은 매수 · 평가일 환율을 날짜 비례로 잇는다")
    void bulkFxException() {
      stubRange(candle(purchaseDate, "100.00"), candle(march, "120.00"), candle(saleDate, "150.00"));
      given(marketDataClientWrapper.getFxRate("2024-06-14")).willReturn(new FxRateDto(saleDate, new BigDecimal("1400")));
      given(marketDataClientWrapper.getBulkFxRates(anyList())).willThrow(new RuntimeException("market-data down"));

      SimulationResponse response = service.runSimulation(request(false, "0"), false);

      assertThat(response.getOptimalBuyDate()).isNull();
      assertThat(response.getDailyValues().get(1).valueKrw()).isEqualByComparingTo("1596556");
    }

    @Test
    @DisplayName("매수 환율이 기본값이어도 원화 연평균 수익률은 투자 원금을 기준으로 낸다")
    void defaultedPurchaseFxCagr() {
      // 130만원을 기본 1350 으로 962.96달러, 주가는 내내 100달러, 환율 기록은 3월 1일(1100)부터
      LocalDate laterSale = LocalDate.of(2025, 6, 16);
      stubRange(candle(purchaseDate, "100.00"), candle(march, "100.00"), candle(laterSale, "100.00"));
      given(marketDataClientWrapper.getFxRate("2024-01-15"))
        .willReturn(new FxRateDto(purchaseDate, new BigDecimal("1350"), true));
      given(marketDataClientWrapper.getFxRate("2025-06-16")).willReturn(new FxRateDto(laterSale, new BigDecimal("1100")));
      given(marketDataClientWrapper.getBulkFxRates(anyList())).willReturn(Map.of(
        "2024-03-01", new BigDecimal("1100"), "2025-06-16", new BigDecimal("1100")));
      SimulationRequest longer = SimulationRequest.builder().symbol(TEST_SYMBOL).purchaseDate(purchaseDate)
        .saleDate(laterSale).investmentAmount(new BigDecimal("1300000")).reinvestDividends(false)
        .dividendTaxRate(BigDecimal.ZERO).build();

      SimulationResponse response = service.runSimulation(longer, false);

      // 518일에 1,059,256 / 1,300,000
      assertThat(response.getMetricsKrw().cagr()).isEqualByComparingTo("-13.45");
      assertThat(response.getMetricsKrw().mdd()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("수동 환율이면 일별 원화 값은 매수 환율에서 평가일 환율까지 날짜에 비례해 잇는다")
    void manualFxInterpolated() {
      // 주가는 내내 100달러, 시장 환율 1450 은 쓰지 않음
      stubRange(candle(purchaseDate, "100.00"), candle(march, "100.00"), candle(saleDate, "100.00"));
      given(marketDataClientWrapper.getBulkFxRates(anyList())).willReturn(Map.of(
        "2024-01-15", new BigDecimal("1450"), "2024-03-01", new BigDecimal("1450"), "2024-06-14", new BigDecimal("1450")));
      SimulationRequest manual = SimulationRequest.builder().symbol(TEST_SYMBOL).purchaseDate(purchaseDate)
        .saleDate(saleDate).investmentAmount(new BigDecimal("1300000")).purchaseFxRate(new BigDecimal("1300"))
        .currentFxRate(new BigDecimal("1400")).build();

      SimulationResponse response = service.runSimulation(manual, false);

      // 1월 15일 ~ 6월 14일 151일 중 46일 → 1300 + 100 × 46/151 = 1330.46
      assertThat(response.getDailyValues().get(1).valueKrw()).isEqualByComparingTo("1330464");
      // 환율이 고르게 올라 원화 낙폭 없음
      assertThat(response.getMetricsKrw().mdd()).isEqualByComparingTo("0.00");
      assertThat(response.getMetricsKrw().mddPeakDate()).isNull();
    }

    @Test
    @DisplayName("환율 기록이 시작되기 전 날은 첫 기록 환율로 세어 원화 낙폭이 없다")
    void fxBeforeFirstRecord() {
      // 주가는 내내 100달러, 환율 기록은 3월 1일(1100)부터라 매수 환율은 기본 1350
      stubRange(candle(purchaseDate, "100.00"), candle(march, "100.00"), candle(saleDate, "100.00"));
      given(marketDataClientWrapper.getFxRate("2024-01-15"))
        .willReturn(new FxRateDto(purchaseDate, new BigDecimal("1350"), true));
      given(marketDataClientWrapper.getFxRate("2024-06-14")).willReturn(new FxRateDto(saleDate, new BigDecimal("1100")));
      given(marketDataClientWrapper.getBulkFxRates(anyList())).willReturn(Map.of(
        "2024-03-01", new BigDecimal("1100"), "2024-06-14", new BigDecimal("1100")));

      SimulationResponse response = service.runSimulation(request(false, "0"), false);

      assertThat(response.getMetricsKrw().mdd()).isEqualByComparingTo("0.00");
      assertThat(response.getDailyValues().getFirst().drawdownKrw()).isEqualByComparingTo("0");
      assertThat(response.getDailyValues().get(1).drawdownKrw()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("기간 일봉을 못 받으면 지표를 내지 않는다")
    void noCandlesNoMetrics() {
      stubRange();
      given(marketDataClientWrapper.getOHLCPrice(TEST_SYMBOL, "2024-01-15"))
        .willReturn(candle(purchaseDate, "100.00"));
      given(marketDataClientWrapper.getOHLCPrice(TEST_SYMBOL, "2024-06-14"))
        .willReturn(candle(saleDate, "150.00"));

      SimulationResponse response = service.runSimulation(request(false, "0"), false);

      assertThat(response.getDailyValues()).hasSize(1);
      assertThat(response.getMetricsKrw().mdd()).isNull();
      assertThat(response.getMetricsUsd().volatility()).isNull();
    }

    @Test
    @DisplayName("종가가 0 인 일봉은 일별 가치에서 뺀다")
    void skipsUnusableCandle() {
      stubRange(candle(purchaseDate, "100.00"), candle(march, "0.00"), candle(saleDate, "150.00"));

      SimulationResponse response = service.runSimulation(request(false, "0"), false);

      assertThat(response.getDailyValues()).extracting(DailyValue::date).containsExactly(purchaseDate, saleDate);
    }
  }

  @Nested
  @DisplayName("매수일별 수익률")
  class PurchaseDayReturnTests {

    private final LocalDate purchaseDate = LocalDate.of(2024, 1, 15);
    private final LocalDate march = LocalDate.of(2024, 3, 15);
    private final LocalDate exDate = LocalDate.of(2024, 4, 10);
    private final LocalDate may = LocalDate.of(2024, 5, 10);
    private final LocalDate saleDate = LocalDate.of(2024, 6, 14);
    private TradingSimulationServiceImpl service;

    @BeforeEach
    void setUpService() {
      service = new TradingSimulationServiceImpl(marketDataClientWrapper, new ResponseMapper(), backtestHistoryUtils);
      // 산 날 1300 · 평가일 1350, 그 사이 날은 기간 환율 1350(setUp), 테스트마다 바꿔 쓰는 준비라 lenient
      lenient().when(marketDataClientWrapper.getOHLCPriceRange(eq(TEST_SYMBOL), anyString(), anyString()))
        .thenReturn(List.of(
          createOHLCPrice(TEST_SYMBOL, purchaseDate, new BigDecimal("100.00"), true),
          createOHLCPrice(TEST_SYMBOL, march, new BigDecimal("80.00"), true),
          createOHLCPrice(TEST_SYMBOL, exDate, new BigDecimal("110.00"), true),
          createOHLCPrice(TEST_SYMBOL, may, new BigDecimal("120.00"), true),
          createOHLCPrice(TEST_SYMBOL, saleDate, new BigDecimal("150.00"), true)));
      lenient().when(marketDataClientWrapper.getFxRate("2024-01-15")).thenReturn(new FxRateDto(purchaseDate, new BigDecimal("1300")));
      lenient().when(marketDataClientWrapper.getFxRate("2024-06-14")).thenReturn(new FxRateDto(saleDate, new BigDecimal("1350")));
      lenient().when(marketDataClientWrapper.getDividendHistory(TEST_SYMBOL, "2024-01-15", "2024-06-14"))
        .thenReturn(List.of(new DividendDto(TEST_SYMBOL, exDate, exDate.plusDays(5), null, new BigDecimal("1.00"), "USD", "test")));
    }

    private SimulationResponse run(String feeRate, boolean reinvest, String taxRate) {
      return service.runSimulation(SimulationRequest.builder()
        .symbol(TEST_SYMBOL).purchaseDate(purchaseDate).saleDate(saleDate)
        .investmentAmount(new BigDecimal("1350000"))
        .tradingFeeRate(new BigDecimal(feeRate)).reinvestDividends(reinvest).dividendTaxRate(new BigDecimal(taxRate))
        .build(), false);
    }

    @ParameterizedTest(name = "수수료 {0} · 재투자 {1} · 원천징수 {2}")
    @CsvSource({"0, false, 0", "0.0025, false, 0.15", "0.0025, true, 0.15"})
    @DisplayName("산 날의 점은 위 총 수익률과 같고, 평가일 전 거래일마다 하나씩이다")
    void firstPointMatchesTotalReturn(String feeRate, boolean reinvest, String taxRate) {
      SimulationResponse response = run(feeRate, reinvest, taxRate);

      assertThat(response.getPurchaseDayReturns()).extracting(PurchaseDayReturn::date)
        .containsExactly(purchaseDate, march, exDate, may);
      assertThat(response.getPurchaseDayReturns().getFirst().returnPercent())
        .isEqualByComparingTo(response.getTotalReturnPercent());
    }

    private SimulationResponse runShares(int shares, String feeRate) {
      return service.runSimulation(SimulationRequest.builder()
        .symbol(TEST_SYMBOL).purchaseDate(purchaseDate).saleDate(saleDate)
        .sharesPerPurchase(shares)
        .tradingFeeRate(new BigDecimal(feeRate)).reinvestDividends(false).dividendTaxRate(BigDecimal.ZERO)
        .build(), false);
    }

    @Test
    @DisplayName("주식 수로 사면 그 주식 수를 사고 종가 × 주식 수 + 수수료를 산 날 환율로 바꾼 값이 투자금이며 남은 현금이 없다")
    void sharesSetInvestment() {
      SimulationResponse response = runShares(10, "0.01");

      // 100 × 10주 = 1,000달러 + 수수료 10달러 × 1300 = 1,313,000 원
      assertThat(response.getShares()).isEqualByComparingTo("10");
      assertThat(response.getTradingFee()).isEqualByComparingTo("10.00");
      assertThat(response.getRemainingCash()).isEqualByComparingTo("0");
      assertThat(response.getInvestmentAmount()).isEqualByComparingTo("1313000");
      // 10주 × 150 × 1350 + 배당 10달러 × 1350 = 2,038,500 원
      assertThat(response.getTotalAssetKrw()).isEqualByComparingTo("2038500");
      assertThat(response.getTotalReturnKrw()).isEqualByComparingTo("725500");
      assertThat(response.getTotalReturnPercent()).isEqualByComparingTo("55.26");
      assertThat(response.getPurchaseDayReturns().getFirst().returnPercent())
        .isEqualByComparingTo(response.getTotalReturnPercent());
    }

    @Test
    @DisplayName("주식 수로 사면 매수일별 수익률도 그 날짜 종가로 같은 주식 수를 산 값이다")
    void sharesPurchaseDayReturns() {
      SimulationResponse response = runShares(10, "0.01");

      // 3월 15일 80 × 10주 = 800달러 + 수수료 8달러 → 평가일 1,500달러 + 배당 10달러
      PurchaseDayReturn march15 = response.getPurchaseDayReturns().get(1);
      assertThat(march15.returnPercent()).isEqualByComparingTo("86.88");
      assertThat(march15.returnPercentUsd()).isEqualByComparingTo("86.88");
    }

    @Test
    @DisplayName("배당락일 이후에 산 날은 배당이 없어 주가 · 환율만으로 센다")
    void afterExDateNoDividend() {
      SimulationResponse response = run("0", false, "0");

      // 5월 10일 1,000달러로 120 에 사서 평가일 150 · 환율은 내내 1350
      PurchaseDayReturn may10 = response.getPurchaseDayReturns().get(3);
      assertThat(may10.returnPercent()).isEqualByComparingTo("25.00");
      assertThat(may10.returnPercentUsd()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("배당락일 전에 산 날은 그 배당까지 더해 산 날마다 다른 값이다")
    void beforeExDateGetsDividend() {
      SimulationResponse response = run("0", false, "0");

      // 3월 15일 1,000달러로 80 에 12.5주 → 150 에 1,875달러 + 배당 12.5달러
      PurchaseDayReturn march15 = response.getPurchaseDayReturns().get(1);
      assertThat(march15.returnPercentUsd()).isEqualByComparingTo("88.75");
    }

    // 5월 10일만 기간 환율을 바꾼 기록(나머지 날은 1350)
    private void stubMayFx(String rate) {
      given(marketDataClientWrapper.getBulkFxRates(anyList())).willAnswer(invocation -> {
        List<String> dates = invocation.getArgument(0);
        return dates.stream().distinct().collect(Collectors.toMap(date -> date,
          date -> date.equals(may.toString()) ? new BigDecimal(rate) : new BigDecimal("1350")));
      });
    }

    private SimulationResponse runWithFx(String purchaseFx, String currentFx) {
      return service.runSimulation(SimulationRequest.builder()
        .symbol(TEST_SYMBOL).purchaseDate(purchaseDate).saleDate(saleDate)
        .investmentAmount(new BigDecimal("1350000"))
        .tradingFeeRate(BigDecimal.ZERO).reinvestDividends(false).dividendTaxRate(BigDecimal.ZERO)
        .purchaseFxRate(purchaseFx == null ? null : new BigDecimal(purchaseFx))
        .currentFxRate(currentFx == null ? null : new BigDecimal(currentFx))
        .build(), false);
    }

    @Test
    @DisplayName("산 날이 아닌 날은 그 날짜 시장 환율로 산다")
    void otherDayUsesMarketFx() {
      stubMayFx("1250");

      // 5월 10일 1,350,000 원 ÷ 1250 = 1,080달러로 9주 → 평가일 150 · 1350 에 1,822,500 원
      PurchaseDayReturn may10 = runWithFx(null, null).getPurchaseDayReturns().get(3);
      assertThat(may10.returnPercent()).isEqualByComparingTo("35.00");
      assertThat(may10.returnPercentUsd()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("현재 환율만 넣어도 산 날이 아닌 날은 그 날짜 시장 환율로 산다")
    void currentFxOnlyStillUsesMarketPurchaseFx() {
      stubMayFx("1250");

      // 9주 × 150 × 넣은 평가일 환율 1500 = 2,025,000 원
      assertThat(runWithFx(null, "1500").getPurchaseDayReturns().get(3).returnPercent()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("매수 환율을 넣으면 산 날이 아닌 날도 그 환율로 산다")
    void manualPurchaseFxForEveryDay() {
      // 넣은 1250 으로 1,080달러 · 9주(시장 1350 이면 1,000달러 · 8.33주)
      assertThat(runWithFx("1250", null).getPurchaseDayReturns().get(3).returnPercent()).isEqualByComparingTo("35.00");
    }

    @Test
    @DisplayName("기간 환율을 못 받으면 매수일별 수익률만 비고 결과는 그대로 낸다")
    void bulkFxFailureLeavesPurchaseDaysEmpty() {
      given(marketDataClientWrapper.getBulkFxRates(anyList())).willThrow(new IllegalStateException("fx down"));

      SimulationResponse response = runWithFx(null, null);

      assertThat(response.getPurchaseDayReturns()).isEmpty();
      assertThat(response.getTotalReturnPercent()).isNotNull();
    }

    @Test
    @DisplayName("매수일별 계산이 실패해도 결과는 그대로 내고 매수일별 수익률만 빈다")
    void purchaseDayFailureLeavesItEmpty() {
      // 현재 환율을 넣으면 일별 가치는 보간 환율이라 그대로, 5월 10일 시장 환율 0 은 매수일별 계산만 막음
      stubMayFx("0");

      SimulationResponse response = runWithFx(null, "1350");

      assertThat(response.getPurchaseDayReturns()).isEmpty();
      assertThat(response.getTotalReturnPercent()).isNotNull();
    }

    @Test
    @DisplayName("재투자면 배당락일 전에 산 날은 그 배당을 배당락일 주가로 더 사고, 뒤에 산 날은 그대로다")
    void reinvestOtherDays() {
      SimulationResponse response = run("0", true, "0");

      // 3월 15일 12.5주 + 배당 12.5달러 ÷ 110 = 0.11363636주 → 12.61363636주 × 150
      assertThat(response.getPurchaseDayReturns().get(1).returnPercentUsd()).isEqualByComparingTo("89.20");
      assertThat(response.getPurchaseDayReturns().get(3).returnPercentUsd()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("쓸 수 없는 일봉의 날짜는 매수일별 수익률에서 뺀다")
    void skipsUnusableCandle() {
      LocalDate february = LocalDate.of(2024, 2, 15);
      given(marketDataClientWrapper.getOHLCPriceRange(eq(TEST_SYMBOL), anyString(), anyString()))
        .willReturn(List.of(
          createOHLCPrice(TEST_SYMBOL, purchaseDate, new BigDecimal("100.00"), true),
          createOHLCPrice(TEST_SYMBOL, february, new BigDecimal("90.00"), false),
          createOHLCPrice(TEST_SYMBOL, may, new BigDecimal("120.00"), true),
          createOHLCPrice(TEST_SYMBOL, saleDate, new BigDecimal("150.00"), true)));

      assertThat(run("0", false, "0").getPurchaseDayReturns()).extracting(PurchaseDayReturn::date)
        .containsExactly(purchaseDate, may);
    }
  }
}
