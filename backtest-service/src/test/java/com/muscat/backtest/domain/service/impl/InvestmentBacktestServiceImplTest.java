package com.muscat.backtest.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.enums.type.BacktestType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.util.BacktestHistoryUtils;
import com.muscat.backtest.domain.dto.request.InvestmentRequest;
import com.muscat.backtest.domain.dto.response.InvestmentResponse;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.entity.InvestmentBacktestResult;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.repository.InvestmentBacktestResultRepository;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import com.muscat.backtest.infra.client.TradeServiceClientWrapper;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.backtest.infra.client.dto.HoldingDto;
import com.muscat.backtest.infra.client.dto.TradeDto;
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
@DisplayName("InvestmentBacktestService 단위 테스트")
class InvestmentBacktestServiceImplTest {

  @Mock private MarketDataClientWrapper marketDataClientWrapper;
  @Mock private TradeServiceClientWrapper tradeServiceClientWrapper;
  @Mock private ResponseMapper responseMapper;
  @Mock private BacktestHistoryUtils backtestHistoryUtils;
  @Mock private InvestmentBacktestResultRepository investmentBacktestResultRepository;
  @Mock private ObjectMapper objectMapper;

  @InjectMocks
  private InvestmentBacktestServiceImpl investmentBacktestService;

  private static final String TEST_USER_ID = "test-user@example.com";
  private static final String TEST_SYMBOL = "AAPL";
  private static final String TEST_AUTHORIZATION = "Bearer token123";

  private InvestmentRequest testInvestmentRequest;

  @BeforeEach
  void setUp() {
    testInvestmentRequest = new InvestmentRequest();
    testInvestmentRequest.setUserId(TEST_USER_ID);
  }

  @Nested
  @DisplayName("투자 실행 테스트")
  class ExecuteInvestmentTests {

    @Test
    @DisplayName("Holdings가 있을 때 투자 백테스트가 성공적으로 실행된다")
    void executeInvestment_WithHoldings_Success() throws JsonProcessingException {
      HoldingDto holding = createHoldingDto("AAPL", new BigDecimal("10.5"),
        new BigDecimal("180.00"), new BigDecimal("1890000"));
      List<HoldingDto> holdings = List.of(holding);

      TradeDto trade = createTradeDto("BUY", LocalDate.of(2024, 1, 10));
      List<TradeDto> tradeHistory = List.of(trade);

      StockPriceDto currentPrice = createCurrentPrice("AAPL", new BigDecimal("230.00"));
      FxRateDto purchaseFxRate = new FxRateDto(LocalDate.of(2024, 1, 10), new BigDecimal("1300.00"));
      FxRateDto currentFxRate = new FxRateDto(LocalDate.now(), new BigDecimal("1320.00"));

      SimulationResponse simulationResponse = createSimulationResponse();
      InvestmentResponse investmentResponse = createInvestmentResponse(holding, simulationResponse);

      given(tradeServiceClientWrapper.getPortfolio(TEST_AUTHORIZATION)).willReturn(holdings);
      given(tradeServiceClientWrapper.getTradeHistoryBySymbol(TEST_AUTHORIZATION, "AAPL"))
        .willReturn(tradeHistory);
      given(marketDataClientWrapper.getCurrentPrice("AAPL")).willReturn(currentPrice);
      given(marketDataClientWrapper.getFxRate(anyString())).willReturn(purchaseFxRate);
      given(marketDataClientWrapper.getLatestFxRate()).willReturn(currentFxRate);
      given(responseMapper.toInvestmentResponse(eq(holding), any(SimulationResponse.class)))
        .willReturn(investmentResponse);
      given(objectMapper.writeValueAsString(any())).willReturn("{\"status\":\"SUCCESS\"}");

      InvestmentResponse result = investmentBacktestService.executeInvestment(
        testInvestmentRequest, TEST_AUTHORIZATION);

      assertThat(result).isNotNull();
      assertThat(result).isEqualTo(investmentResponse);
      verify(backtestHistoryUtils).saveBacktestHistory(TEST_USER_ID,
        BacktestType.INVESTMENT_ANALYSIS, testInvestmentRequest);
      verify(investmentBacktestResultRepository).save(any(InvestmentBacktestResult.class));
    }

    @Test
    @DisplayName("Holdings가 없을 때 예외가 발생한다")
    void executeInvestment_NoHoldings_ThrowsException() {
      given(tradeServiceClientWrapper.getPortfolio(TEST_AUTHORIZATION))
        .willReturn(Collections.emptyList());

      assertThatThrownBy(() ->
        investmentBacktestService.executeInvestment(testInvestmentRequest, TEST_AUTHORIZATION))
        .isInstanceOf(BacktestException.class)
        .hasMessageContaining("보유 주식")
        .extracting("errorCode")
        .isEqualTo(BacktestResponse.HOLDING_DATA_NOT_FOUND);

      verify(backtestHistoryUtils, never()).saveBacktestHistory(any(), any(), any());
      verify(investmentBacktestResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("계산 중 오류 발생 시 적절한 예외가 발생한다")
    void executeInvestment_CalculationError_ThrowsException() {
      HoldingDto holding = createHoldingDto("AAPL", new BigDecimal("10.5"),
        new BigDecimal("180.00"), new BigDecimal("1890000"));
      List<HoldingDto> holdings = List.of(holding);

      TradeDto trade = createTradeDto("BUY", LocalDate.of(2024, 1, 10));
      List<TradeDto> tradeHistory = List.of(trade);

      given(tradeServiceClientWrapper.getPortfolio(TEST_AUTHORIZATION)).willReturn(holdings);
      given(tradeServiceClientWrapper.getTradeHistoryBySymbol(TEST_AUTHORIZATION, "AAPL"))
        .willReturn(tradeHistory);
      given(marketDataClientWrapper.getCurrentPrice("AAPL"))
        .willThrow(new RuntimeException("Calculation error occurred"));

      assertThatThrownBy(() ->
        investmentBacktestService.executeInvestment(testInvestmentRequest, TEST_AUTHORIZATION))
        .isInstanceOf(BacktestException.class)
        .hasMessageContaining("계산")
        .extracting("errorCode")
        .isEqualTo(BacktestResponse.CALCULATION_ERROR);
    }
  }

  @Nested
  @DisplayName("캐시된 투자 결과 조회 테스트")
  class GetCachedInvestmentResultTests {

    @Test
    @DisplayName("캐시된 투자 결과가 성공적으로 조회된다")
    void getCachedInvestmentResult_Found_Success() throws JsonProcessingException {
      InvestmentBacktestResult cachedEntity = InvestmentBacktestResult.builder()
        .id(123L).userId(TEST_USER_ID).resultData("{\"status\":\"SUCCESS\"}")
        .calculatedAt(LocalDateTime.now()).build();

      InvestmentResponse expectedResponse = InvestmentResponse.builder().status("SUCCESS").build();

      given(investmentBacktestResultRepository.findByUserId(TEST_USER_ID))
        .willReturn(Optional.of(cachedEntity));
      given(objectMapper.readValue(eq("{\"status\":\"SUCCESS\"}"), eq(InvestmentResponse.class)))
        .willReturn(expectedResponse);

      Optional<InvestmentResponse> result = investmentBacktestService.getCachedInvestmentResult(TEST_USER_ID);

      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(expectedResponse);
      assertThat(result.get().getStatus()).isEqualTo("SUCCESS");
      verify(investmentBacktestResultRepository).findByUserId(TEST_USER_ID);
    }

    @Test
    @DisplayName("캐시된 투자 결과가 없을 때 Optional.empty가 반환된다")
    void getCachedInvestmentResult_NotFound_ReturnsEmpty() {
      given(investmentBacktestResultRepository.findByUserId(TEST_USER_ID)).willReturn(Optional.empty());

      Optional<InvestmentResponse> result = investmentBacktestService.getCachedInvestmentResult(TEST_USER_ID);

      assertThat(result).isEmpty();
      verify(investmentBacktestResultRepository).findByUserId(TEST_USER_ID);
    }

    @Test
    @DisplayName("calculatedAt이 null일 때 Optional.empty가 반환된다")
    void getCachedInvestmentResult_NullCalculatedAt_ReturnsEmpty() {
      InvestmentBacktestResult cachedEntity = InvestmentBacktestResult.builder()
        .id(123L).userId(TEST_USER_ID).resultData("{\"status\":\"SUCCESS\"}")
        .calculatedAt(null).build();

      given(investmentBacktestResultRepository.findByUserId(TEST_USER_ID))
        .willReturn(Optional.of(cachedEntity));

      Optional<InvestmentResponse> result = investmentBacktestService.getCachedInvestmentResult(TEST_USER_ID);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("JSON 파싱 오류 시 Optional.empty가 반환된다")
    void getCachedInvestmentResult_JsonParsingError_ReturnsEmpty() throws JsonProcessingException {
      InvestmentBacktestResult cachedEntity = InvestmentBacktestResult.builder()
        .id(123L).userId(TEST_USER_ID).resultData("invalid json")
        .calculatedAt(LocalDateTime.now()).build();

      given(investmentBacktestResultRepository.findByUserId(TEST_USER_ID))
        .willReturn(Optional.of(cachedEntity));
      given(objectMapper.readValue(eq("invalid json"), eq(InvestmentResponse.class)))
        .willThrow(new JsonProcessingException("Invalid JSON") {});

      Optional<InvestmentResponse> result = investmentBacktestService.getCachedInvestmentResult(TEST_USER_ID);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Repository 조회 중 예외 발생 시 Optional.empty가 반환된다")
    void getCachedInvestmentResult_RepositoryError_ReturnsEmpty() {
      given(investmentBacktestResultRepository.findByUserId(TEST_USER_ID))
        .willThrow(new RuntimeException("Database error"));

      Optional<InvestmentResponse> result = investmentBacktestService.getCachedInvestmentResult(TEST_USER_ID);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("캐시된 투자 결과 Entity 조회 테스트")
  class GetCachedInvestmentResultEntityTests {

    @Test
    @DisplayName("캐시된 투자 결과 Entity가 성공적으로 조회된다")
    void getCachedInvestmentResultEntity_Found_Success() {
      InvestmentBacktestResult cachedEntity = InvestmentBacktestResult.builder()
        .id(123L).userId(TEST_USER_ID).resultData("{\"status\":\"SUCCESS\"}")
        .calculatedAt(LocalDateTime.now()).build();

      given(investmentBacktestResultRepository.findByUserId(TEST_USER_ID))
        .willReturn(Optional.of(cachedEntity));

      Optional<InvestmentBacktestResult> result = investmentBacktestService
        .getCachedInvestmentResultEntity(TEST_USER_ID);

      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(cachedEntity);
      assertThat(result.get().getUserId()).isEqualTo(TEST_USER_ID);
      assertThat(result.get().getId()).isEqualTo(123L);
    }

    @Test
    @DisplayName("캐시된 투자 결과 Entity가 없을 때 Optional.empty가 반환된다")
    void getCachedInvestmentResultEntity_NotFound_ReturnsEmpty() {
      given(investmentBacktestResultRepository.findByUserId(TEST_USER_ID)).willReturn(Optional.empty());

      Optional<InvestmentBacktestResult> result = investmentBacktestService
        .getCachedInvestmentResultEntity(TEST_USER_ID);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("calculatedAt이 null인 Entity는 반환되지 않는다")
    void getCachedInvestmentResultEntity_NullCalculatedAt_ReturnsEmpty() {
      InvestmentBacktestResult cachedEntity = InvestmentBacktestResult.builder()
        .id(123L).userId(TEST_USER_ID).resultData("{\"status\":\"SUCCESS\"}")
        .calculatedAt(null).build();

      given(investmentBacktestResultRepository.findByUserId(TEST_USER_ID))
        .willReturn(Optional.of(cachedEntity));

      Optional<InvestmentBacktestResult> result = investmentBacktestService
        .getCachedInvestmentResultEntity(TEST_USER_ID);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Repository 조회 중 예외 발생 시 Optional.empty가 반환된다")
    void getCachedInvestmentResultEntity_RepositoryError_ReturnsEmpty() {
      given(investmentBacktestResultRepository.findByUserId(TEST_USER_ID))
        .willThrow(new RuntimeException("Database error"));

      Optional<InvestmentBacktestResult> result = investmentBacktestService
        .getCachedInvestmentResultEntity(TEST_USER_ID);

      assertThat(result).isEmpty();
    }
  }

  // === Helper Methods ===

  private StockPriceDto createCurrentPrice(String symbol, BigDecimal currentPrice) {
    return new StockPriceDto(
      symbol, "Test Stock", currentPrice, currentPrice.subtract(new BigDecimal("2.00")),
      new BigDecimal("2.00"), new BigDecimal("0.87"), null, null, LocalDateTime.now(),
      null, null, null, null, null, "USD", true, null, null);
  }

  private SimulationResponse createSimulationResponse() {
    return SimulationResponse.builder()
      .symbol(TEST_SYMBOL).purchaseDate(LocalDate.of(2024, 1, 15)).currentDate(LocalDate.now())
      .investmentAmount(new BigDecimal("1000000")).purchasePrice(new BigDecimal("180.00"))
      .shares(new BigDecimal("41.7")).currentPrice(new BigDecimal("230.00"))
      .currentValue(new BigDecimal("9591.00")).stockReturn(new BigDecimal("2085.00"))
      .stockReturnPercent(new BigDecimal("27.78")).purchaseFxRate(new BigDecimal("1300.00"))
      .currentFxRate(new BigDecimal("1320.00")).fxReturn(new BigDecimal("20.00"))
      .fxReturnPercent(new BigDecimal("1.54")).totalDividends(BigDecimal.ZERO)
      .dividendYield(BigDecimal.ZERO).tradingFee(new BigDecimal("18.82"))
      .remainingCash(new BigDecimal("498.17")).totalReturn(new BigDecimal("2583.17"))
      .totalReturnPercent(new BigDecimal("29.32")).currentValueKrw(new BigDecimal("12660120"))
      .remainingCashKrw(new BigDecimal("657588")).totalAssetKrw(new BigDecimal("13317708"))
      .totalReturnKrw(new BigDecimal("3317708"))
      .performanceSummary("총 수익: $2583.17 (29.32%), 환차익: 1.54%")
      .build();
  }

  private HoldingDto createHoldingDto(String symbol, BigDecimal shares, BigDecimal avgPrice,
    BigDecimal totalInvested) {
    return new HoldingDto(
      "holding-123", "account-456", symbol, shares, avgPrice, totalInvested,
      BigDecimal.ZERO, null, LocalDateTime.of(2024, 1, 15, 10, 0));
  }

  private TradeDto createTradeDto(String tradeType, LocalDate tradeDate) {
    TradeDto dto = new TradeDto();
    dto.setTradeId("trade-123");
    dto.setTradeType(tradeType);
    dto.setTradeDate(tradeDate);
    return dto;
  }

  private InvestmentResponse createInvestmentResponse(HoldingDto holding,
    SimulationResponse simulation) {
    return InvestmentResponse.builder()
      .simulation(simulation).holdingId(holding.holdingId()).symbol(holding.symbol())
      .purchaseDate(null).investmentAmount(holding.getTotalInvested())
      .purchasePrice(holding.getAveragePrice()).shares(holding.getShares())
      .status("SUCCESS").message("투자 백테스트가 성공적으로 완료되었습니다")
      .portfolioCreated(true).portfolioStatus("ACTIVE")
      .build();
  }
}
