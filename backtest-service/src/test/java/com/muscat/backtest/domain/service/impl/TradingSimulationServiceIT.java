package com.muscat.backtest.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.domain.dto.request.InvestmentRequest;
import com.muscat.backtest.domain.dto.response.InvestmentResponse;
import com.muscat.backtest.domain.entity.InvestmentBacktestResult;
import com.muscat.backtest.domain.repository.InvestmentBacktestResultRepository;
import com.muscat.backtest.infra.client.MarketDataClient;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import com.muscat.backtest.infra.client.TradeServiceClientWrapper;
import com.muscat.backtest.infra.client.dto.HoldingDto;
import com.muscat.backtest.infra.client.dto.TradeDto;
import com.muscat.commonlib.dto.StockPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("TradingSimulationService 통합 테스트")
class TradingSimulationServiceIT {

  @Autowired
  private TradingSimulationServiceImpl tradingSimulationService;

  @Autowired
  private InvestmentBacktestResultRepository investmentBacktestResultRepository;

  @MockBean
  private MarketDataClientWrapper marketDataClientWrapper;

  @MockBean
  private TradeServiceClientWrapper tradeServiceClientWrapper;

  private static final String TEST_USER_ID = "integration-test-user@example.com";
  private static final String TEST_SYMBOL = "AAPL";
  private static final String TEST_AUTHORIZATION = "Bearer integration-token";

  private InvestmentRequest testInvestmentRequest;

  @BeforeEach
  void setUp() {
    testInvestmentRequest = new InvestmentRequest();
    testInvestmentRequest.setUserId(TEST_USER_ID);
  }

  @AfterEach
  void tearDown() {
    investmentBacktestResultRepository.deleteAll();
  }

  @Nested
  @DisplayName("투자 실행 통합 테스트")
  class ExecuteInvestmentIntegrationTests {

    @Test
    @DisplayName("투자 실행 시 DB에 결과가 저장되고 조회할 수 있다")
    void executeInvestment_SavesResultToDatabase() {
      // given
      HoldingDto holding = createHoldingDto(TEST_SYMBOL, new BigDecimal("10.5"),
        new BigDecimal("180.00"), new BigDecimal("1890000"));
      List<HoldingDto> holdings = List.of(holding);

      TradeDto trade = createTradeDto("BUY", LocalDate.of(2024, 1, 10));
      List<TradeDto> tradeHistory = List.of(trade);

      StockPriceDto currentPrice = createCurrentPrice(TEST_SYMBOL, new BigDecimal("230.00"));
      MarketDataClient.FxRate purchaseFxRate = new MarketDataClient.FxRate(
        LocalDate.of(2024, 1, 10), new BigDecimal("1300.00"));
      MarketDataClient.FxRate currentFxRate = new MarketDataClient.FxRate(
        LocalDate.now(), new BigDecimal("1320.00"));

      given(tradeServiceClientWrapper.getPortfolio(TEST_AUTHORIZATION))
        .willReturn(holdings);
      given(tradeServiceClientWrapper.getTradeHistoryBySymbol(TEST_AUTHORIZATION, TEST_SYMBOL))
        .willReturn(tradeHistory);
      given(marketDataClientWrapper.getCurrentPrice(TEST_SYMBOL))
        .willReturn(currentPrice);
      given(marketDataClientWrapper.getFxRate(anyString()))
        .willReturn(purchaseFxRate);
      given(marketDataClientWrapper.getLatestFxRate())
        .willReturn(currentFxRate);

      // when
      InvestmentResponse result = tradingSimulationService.executeInvestment(
        testInvestmentRequest, TEST_AUTHORIZATION);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getSymbol()).isEqualTo(TEST_SYMBOL);
      assertThat(result.getStatus()).isEqualTo("SUCCESS");

      // DB에 저장되었는지 확인
      Optional<InvestmentBacktestResult> savedEntity =
        investmentBacktestResultRepository.findByUserId(TEST_USER_ID);

      assertThat(savedEntity).isPresent();
      assertThat(savedEntity.get().getUserId()).isEqualTo(TEST_USER_ID);
      assertThat(savedEntity.get().getCalculatedAt()).isNotNull();
      assertThat(savedEntity.get().getResultData()).isNotNull();
    }

    @Test
    @DisplayName("동일 유저의 투자 결과는 덮어쓰기된다")
    void executeInvestment_OverwritesPreviousResult() {
      // given - 기존 결과 저장
      InvestmentBacktestResult existingResult = InvestmentBacktestResult.builder()
        .userId(TEST_USER_ID)
        .resultData("{\"symbol\":\"OLD\"}")
        .calculatedAt(LocalDateTime.now().minusDays(1))
        .build();
      investmentBacktestResultRepository.save(existingResult);
      investmentBacktestResultRepository.flush();

      HoldingDto holding = createHoldingDto(TEST_SYMBOL, new BigDecimal("10.5"),
        new BigDecimal("180.00"), new BigDecimal("1890000"));
      List<HoldingDto> holdings = List.of(holding);

      TradeDto trade = createTradeDto("BUY", LocalDate.of(2024, 1, 10));
      List<TradeDto> tradeHistory = List.of(trade);

      StockPriceDto currentPrice = createCurrentPrice(TEST_SYMBOL, new BigDecimal("230.00"));
      MarketDataClient.FxRate purchaseFxRate = new MarketDataClient.FxRate(
        LocalDate.of(2024, 1, 10), new BigDecimal("1300.00"));
      MarketDataClient.FxRate currentFxRate = new MarketDataClient.FxRate(
        LocalDate.now(), new BigDecimal("1320.00"));

      given(tradeServiceClientWrapper.getPortfolio(TEST_AUTHORIZATION))
        .willReturn(holdings);
      given(tradeServiceClientWrapper.getTradeHistoryBySymbol(TEST_AUTHORIZATION, TEST_SYMBOL))
        .willReturn(tradeHistory);
      given(marketDataClientWrapper.getCurrentPrice(TEST_SYMBOL))
        .willReturn(currentPrice);
      given(marketDataClientWrapper.getFxRate(anyString()))
        .willReturn(purchaseFxRate);
      given(marketDataClientWrapper.getLatestFxRate())
        .willReturn(currentFxRate);

      // when
      tradingSimulationService.executeInvestment(testInvestmentRequest, TEST_AUTHORIZATION);

      // then - 결과가 하나만 있고, 새로운 데이터로 업데이트되었는지 확인
      List<InvestmentBacktestResult> allResults = investmentBacktestResultRepository.findAll();
      assertThat(allResults).hasSize(1);
      assertThat(allResults.get(0).getResultData()).contains("AAPL");
      assertThat(allResults.get(0).getResultData()).doesNotContain("\"OLD\"");
    }

    @Test
    @DisplayName("보유 주식이 없을 때 예외 발생하고 DB에 저장되지 않는다")
    void executeInvestment_NoHoldings_ThrowsExceptionAndDoesNotSave() {
      // given
      given(tradeServiceClientWrapper.getPortfolio(TEST_AUTHORIZATION))
        .willReturn(Collections.emptyList());

      long countBefore = investmentBacktestResultRepository.count();

      // when & then
      assertThatThrownBy(() ->
        tradingSimulationService.executeInvestment(testInvestmentRequest, TEST_AUTHORIZATION))
        .isInstanceOf(BacktestException.class)
        .extracting("errorCode")
        .isEqualTo(BacktestResponse.HOLDING_DATA_NOT_FOUND);

      // DB에 저장되지 않았는지 확인
      long countAfter = investmentBacktestResultRepository.count();
      assertThat(countAfter).isEqualTo(countBefore);
    }
  }

  @Nested
  @DisplayName("캐시된 투자 결과 조회 통합 테스트")
  class GetCachedInvestmentResultIntegrationTests {

    @Test
    @DisplayName("실제 투자 실행 후 저장된 데이터를 조회할 수 있다")
    void getCachedInvestmentResult_AfterExecution_Success() {
      // given - 실제 투자 실행
      HoldingDto holding = createHoldingDto(TEST_SYMBOL, new BigDecimal("10.5"),
        new BigDecimal("180.00"), new BigDecimal("1890000"));
      List<HoldingDto> holdings = List.of(holding);

      TradeDto trade = createTradeDto("BUY", LocalDate.of(2024, 1, 10));
      List<TradeDto> tradeHistory = List.of(trade);

      StockPriceDto currentPrice = createCurrentPrice(TEST_SYMBOL, new BigDecimal("230.00"));
      MarketDataClient.FxRate purchaseFxRate = new MarketDataClient.FxRate(
        LocalDate.of(2024, 1, 10), new BigDecimal("1300.00"));
      MarketDataClient.FxRate currentFxRate = new MarketDataClient.FxRate(
        LocalDate.now(), new BigDecimal("1320.00"));

      given(tradeServiceClientWrapper.getPortfolio(TEST_AUTHORIZATION))
        .willReturn(holdings);
      given(tradeServiceClientWrapper.getTradeHistoryBySymbol(TEST_AUTHORIZATION, TEST_SYMBOL))
        .willReturn(tradeHistory);
      given(marketDataClientWrapper.getCurrentPrice(TEST_SYMBOL))
        .willReturn(currentPrice);
      given(marketDataClientWrapper.getFxRate(anyString()))
        .willReturn(purchaseFxRate);
      given(marketDataClientWrapper.getLatestFxRate())
        .willReturn(currentFxRate);

      // when - 투자 실행
      InvestmentResponse executionResult = tradingSimulationService.executeInvestment(
        testInvestmentRequest, TEST_AUTHORIZATION);

      // DB에서 저장된 raw JSON 확인
      Optional<InvestmentBacktestResult> savedEntity =
        investmentBacktestResultRepository.findByUserId(TEST_USER_ID);
      assertThat(savedEntity).isPresent();
      System.out.println("Saved JSON: " + savedEntity.get().getResultData());

      // when - 캐시 조회
      Optional<InvestmentResponse> cachedResult =
        tradingSimulationService.getCachedInvestmentResult(TEST_USER_ID);

      // then
      assertThat(cachedResult).as("캐시된 결과가 조회되어야 함").isPresent();
      assertThat(cachedResult.get().getSymbol()).isEqualTo(TEST_SYMBOL);
      assertThat(cachedResult.get().getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("DB에 결과가 없을 때 Optional.empty를 반환한다")
    void getCachedInvestmentResult_NotFound_ReturnsEmpty() {
      // given - 아무것도 저장하지 않음

      // when
      Optional<InvestmentResponse> result =
        tradingSimulationService.getCachedInvestmentResult("non-existent-user");

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("잘못된 JSON 형식일 때 Optional.empty가 반환된다")
    void getCachedInvestmentResult_InvalidJson_ReturnsEmpty() {
      // given - 잘못된 JSON 형식으로 저장
      InvestmentBacktestResult entity = InvestmentBacktestResult.builder()
        .userId(TEST_USER_ID)
        .resultData("invalid json format")
        .calculatedAt(LocalDateTime.now())
        .build();
      investmentBacktestResultRepository.save(entity);
      investmentBacktestResultRepository.flush();

      // when
      Optional<InvestmentResponse> result =
        tradingSimulationService.getCachedInvestmentResult(TEST_USER_ID);

      // then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("전체 플로우 통합 테스트")
  class EndToEndFlowTests {

    @Test
    @DisplayName("투자 실행 후 캐시된 결과를 조회할 수 있다")
    void executeAndRetrieve_EndToEndFlow() {
      // given - 투자 실행 준비
      HoldingDto holding = createHoldingDto(TEST_SYMBOL, new BigDecimal("10.5"),
        new BigDecimal("180.00"), new BigDecimal("1890000"));
      List<HoldingDto> holdings = List.of(holding);

      TradeDto trade = createTradeDto("BUY", LocalDate.of(2024, 1, 10));
      List<TradeDto> tradeHistory = List.of(trade);

      StockPriceDto currentPrice = createCurrentPrice(TEST_SYMBOL, new BigDecimal("230.00"));
      MarketDataClient.FxRate purchaseFxRate = new MarketDataClient.FxRate(
        LocalDate.of(2024, 1, 10), new BigDecimal("1300.00"));
      MarketDataClient.FxRate currentFxRate = new MarketDataClient.FxRate(
        LocalDate.now(), new BigDecimal("1320.00"));

      given(tradeServiceClientWrapper.getPortfolio(TEST_AUTHORIZATION))
        .willReturn(holdings);
      given(tradeServiceClientWrapper.getTradeHistoryBySymbol(TEST_AUTHORIZATION, TEST_SYMBOL))
        .willReturn(tradeHistory);
      given(marketDataClientWrapper.getCurrentPrice(TEST_SYMBOL))
        .willReturn(currentPrice);
      given(marketDataClientWrapper.getFxRate(anyString()))
        .willReturn(purchaseFxRate);
      given(marketDataClientWrapper.getLatestFxRate())
        .willReturn(currentFxRate);

      // when - 투자 실행
      InvestmentResponse executionResult = tradingSimulationService.executeInvestment(
        testInvestmentRequest, TEST_AUTHORIZATION);

      // then - 실행 결과 확인
      assertThat(executionResult).isNotNull();
      assertThat(executionResult.getSymbol()).isEqualTo(TEST_SYMBOL);

      // when - 캐시된 결과 조회
      Optional<InvestmentResponse> cachedResult =
        tradingSimulationService.getCachedInvestmentResult(TEST_USER_ID);

      // then - 조회된 결과가 실행 결과와 동일한지 확인
      assertThat(cachedResult).isPresent();
      assertThat(cachedResult.get().getSymbol()).isEqualTo(executionResult.getSymbol());
      assertThat(cachedResult.get().getStatus()).isEqualTo(executionResult.getStatus());
    }

    @Test
    @DisplayName("여러 사용자의 투자 결과가 각각 독립적으로 저장된다")
    void multipleUsers_IndependentResults() {
      // given - 두 명의 사용자
      String user1 = "user1@example.com";
      String user2 = "user2@example.com";

      InvestmentRequest request1 = new InvestmentRequest();
      request1.setUserId(user1);

      InvestmentRequest request2 = new InvestmentRequest();
      request2.setUserId(user2);

      HoldingDto holding = createHoldingDto(TEST_SYMBOL, new BigDecimal("10.5"),
        new BigDecimal("180.00"), new BigDecimal("1890000"));
      List<HoldingDto> holdings = List.of(holding);

      TradeDto trade = createTradeDto("BUY", LocalDate.of(2024, 1, 10));
      List<TradeDto> tradeHistory = List.of(trade);

      StockPriceDto currentPrice = createCurrentPrice(TEST_SYMBOL, new BigDecimal("230.00"));
      MarketDataClient.FxRate purchaseFxRate = new MarketDataClient.FxRate(
        LocalDate.of(2024, 1, 10), new BigDecimal("1300.00"));
      MarketDataClient.FxRate currentFxRate = new MarketDataClient.FxRate(
        LocalDate.now(), new BigDecimal("1320.00"));

      given(tradeServiceClientWrapper.getPortfolio(any()))
        .willReturn(holdings);
      given(tradeServiceClientWrapper.getTradeHistoryBySymbol(any(), eq(TEST_SYMBOL)))
        .willReturn(tradeHistory);
      given(marketDataClientWrapper.getCurrentPrice(TEST_SYMBOL))
        .willReturn(currentPrice);
      given(marketDataClientWrapper.getFxRate(anyString()))
        .willReturn(purchaseFxRate);
      given(marketDataClientWrapper.getLatestFxRate())
        .willReturn(currentFxRate);

      // when - 두 사용자 모두 투자 실행
      tradingSimulationService.executeInvestment(request1, "Bearer token1");
      tradingSimulationService.executeInvestment(request2, "Bearer token2");

      // then - 두 결과가 각각 독립적으로 저장되었는지 확인
      List<InvestmentBacktestResult> allResults = investmentBacktestResultRepository.findAll();
      assertThat(allResults).hasSize(2);

      Optional<InvestmentResponse> result1 =
        tradingSimulationService.getCachedInvestmentResult(user1);
      Optional<InvestmentResponse> result2 =
        tradingSimulationService.getCachedInvestmentResult(user2);

      assertThat(result1).isPresent();
      assertThat(result2).isPresent();
    }
  }

  // Helper Methods

  private HoldingDto createHoldingDto(String symbol, BigDecimal shares, BigDecimal avgPrice,
    BigDecimal totalInvested) {
    return new HoldingDto(
      "holding-123",
      "account-456",
      symbol,
      shares,
      avgPrice,
      totalInvested,
      BigDecimal.ZERO,
      null,
      LocalDateTime.of(2024, 1, 15, 10, 0)
    );
  }

  private TradeDto createTradeDto(String tradeType, LocalDate tradeDate) {
    TradeDto dto = new TradeDto();
    dto.setTradeId("trade-123");
    dto.setTradeType(tradeType);
    dto.setTradeDate(tradeDate);
    return dto;
  }

  private StockPriceDto createCurrentPrice(String symbol, BigDecimal currentPrice) {
    return new StockPriceDto(
      symbol,
      "Test Stock",
      currentPrice,
      currentPrice.subtract(new BigDecimal("2.00")),
      new BigDecimal("2.00"),
      new BigDecimal("0.87"),
      null,
      null,
      LocalDateTime.now(),
      null,
      null,
      null,
      null,
      null,
      "USD",
      true,
      null,
      null
    );
  }
}
