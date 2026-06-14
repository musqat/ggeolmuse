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
import static org.mockito.Mockito.times;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.enums.type.BacktestType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.util.BacktestHistoryUtils;
import com.muscat.backtest.domain.dto.request.InvestmentRequest;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.response.InvestmentResponse;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.entity.InvestmentBacktestResult;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.repository.InvestmentBacktestResultRepository;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import com.muscat.backtest.infra.client.dto.DividendDto;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.backtest.infra.client.TradeServiceClientWrapper;
import com.muscat.backtest.infra.client.dto.HoldingDto;
import com.muscat.backtest.infra.client.dto.TradeDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.dto.StockPriceDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradingSimulationService 단위 테스트")
class TradingSimulationServiceImplTest {

  @Mock
  private MarketDataClientWrapper marketDataClientWrapper;

  @Mock
  private TradeServiceClientWrapper tradeServiceClientWrapper;

  @Mock
  private ResponseMapper responseMapper;

  @Mock
  private BacktestHistoryUtils backtestHistoryUtils;

  @Mock
  private InvestmentBacktestResultRepository investmentBacktestResultRepository;

  @Mock
  private ObjectMapper objectMapper;

  @InjectMocks
  private TradingSimulationServiceImpl tradingSimulationService;

  private static final String TEST_USER_ID = "test-user@example.com";
  private static final String TEST_SYMBOL = "AAPL";
  private static final String TEST_AUTHORIZATION = "Bearer token123";

  private SimulationRequest testSimulationRequest;
  private InvestmentRequest testInvestmentRequest;

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

    testInvestmentRequest = new InvestmentRequest();
    testInvestmentRequest.setUserId(TEST_USER_ID);
  }

  @Nested
  @DisplayName("시뮬레이션 실행 테스트")
  class RunSimulationTests {

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

      given(marketDataClientWrapper.getOHLCPrice(eq(TEST_SYMBOL), anyString()))
        .willReturn(purchasePrice);
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

      given(marketDataClientWrapper.getOHLCPrice(eq(TEST_SYMBOL), anyString()))
        .willReturn(purchasePrice);
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
      verify(marketDataClientWrapper).getOHLCPrice(TEST_SYMBOL,
        testSimulationRequest.getPurchaseDate().toString());
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

      given(marketDataClientWrapper.getOHLCPrice(eq(TEST_SYMBOL), anyString()))
        .willReturn(purchasePrice);
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

      // then: 배당일 주가는 BULK 맵에서 조회 → 단건 getOHLCPrice는 매수가 1회뿐(배당마다 호출 안 함)
      assertThat(result).isNotNull();
      verify(marketDataClientWrapper, times(1)).getOHLCPrice(eq(TEST_SYMBOL), anyString());
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

      given(marketDataClientWrapper.getOHLCPrice(eq(TEST_SYMBOL), anyString()))
        .willReturn(purchasePrice);
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

      given(marketDataClientWrapper.getOHLCPrice(eq(TEST_SYMBOL), anyString()))
        .willReturn(purchasePrice);
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

      given(marketDataClientWrapper.getOHLCPrice(eq(TEST_SYMBOL), anyString()))
        .willReturn(purchasePrice);
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
      .totalReturn(new BigDecimal("2583.17"))
      .totalReturnPercent(new BigDecimal("29.32"))
      .currentValueKrw(new BigDecimal("12660120"))
      .remainingCashKrw(new BigDecimal("657588"))
      .totalAssetKrw(new BigDecimal("13317708"))
      .totalReturnKrw(new BigDecimal("3317708"))
      .performanceSummary("총 수익: $2583.17 (29.32%), 환차익: 1.54%")
      .build();
  }

}
