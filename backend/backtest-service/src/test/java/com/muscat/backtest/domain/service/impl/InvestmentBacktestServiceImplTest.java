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
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

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

  private static final String TEST_USER_ID = "kc-subject-1";
  private static final String TEST_EMAIL = "owner@example.com";
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
    @DisplayName("보유 종목이 있으면 백테스트를 돌려 결과는 토큰 사용자로, 이력은 이메일로 남긴다")
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
        testInvestmentRequest, TEST_AUTHORIZATION, TEST_EMAIL);

      assertThat(result).isNotNull();
      assertThat(result).isEqualTo(investmentResponse);
      verify(backtestHistoryUtils).saveBacktestHistory(TEST_EMAIL,
        BacktestType.INVESTMENT_ANALYSIS, testInvestmentRequest);
      ArgumentCaptor<InvestmentBacktestResult> saved = ArgumentCaptor.forClass(InvestmentBacktestResult.class);
      verify(investmentBacktestResultRepository).save(saved.capture());
      assertThat(saved.getValue().getUserId()).isEqualTo(TEST_USER_ID);
    }

    @Test
    @DisplayName("Holdings가 없을 때 예외가 발생한다")
    void executeInvestment_NoHoldings_ThrowsException() {
      given(tradeServiceClientWrapper.getPortfolio(TEST_AUTHORIZATION))
        .willReturn(Collections.emptyList());

      assertThatThrownBy(() ->
        investmentBacktestService.executeInvestment(testInvestmentRequest, TEST_AUTHORIZATION, TEST_EMAIL))
        .isInstanceOf(BacktestException.class)
        .hasMessageContaining("보유 주식")
        .extracting("code")
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
        investmentBacktestService.executeInvestment(testInvestmentRequest, TEST_AUTHORIZATION, TEST_EMAIL))
        .isInstanceOf(BacktestException.class)
        .hasMessageContaining("계산")
        .extracting("code")
        .isEqualTo(BacktestResponse.CALCULATION_ERROR);
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

  // 보유 종목 하나의 성과 계산에 필요한 시세 · 환율을 깔고, 응답 매퍼가 받은 계산 결과를 잡는다
  private SimulationResponse capturedSimulation(HoldingDto holding) {
    ArgumentCaptor<SimulationResponse> captor = ArgumentCaptor.forClass(SimulationResponse.class);
    verify(responseMapper).toInvestmentResponse(eq(holding), captor.capture());
    return captor.getValue();
  }

  private void stubSingleHolding(HoldingDto holding) {
    given(tradeServiceClientWrapper.getPortfolio(TEST_AUTHORIZATION)).willReturn(List.of(holding));
    given(tradeServiceClientWrapper.getTradeHistoryBySymbol(TEST_AUTHORIZATION, holding.symbol())).willReturn(List.of());
    given(marketDataClientWrapper.getCurrentPrice(holding.symbol()))
      .willReturn(createCurrentPrice(holding.symbol(), new BigDecimal("200.00")));
    given(responseMapper.toInvestmentResponse(eq(holding), any(SimulationResponse.class)))
      .willReturn(InvestmentResponse.builder().symbol(holding.symbol()).status("SUCCESS").build());
  }

  @Nested
  @DisplayName("보유 종목 여럿의 포트폴리오 합산")
  class PortfolioMergeTests {

    private final HoldingDto aapl = createHoldingDto("AAPL", new BigDecimal("5"), new BigDecimal("150"), new BigDecimal("1000000"));
    private final HoldingDto msft = createHoldingDto("MSFT", new BigDecimal("4"), new BigDecimal("300"), new BigDecimal("2000000"));

    private SimulationResponse sim(LocalDate purchase, LocalDate current, String invested, String valueUsd, String valueKrw,
      String dividends, String fee, String cash, String cashKrw, String fx) {
      return SimulationResponse.builder().purchaseDate(purchase).currentDate(current)
        .investmentAmount(new BigDecimal(invested)).currentValue(new BigDecimal(valueUsd))
        .currentValueKrw(new BigDecimal(valueKrw)).totalDividends(new BigDecimal(dividends))
        .tradingFee(new BigDecimal(fee)).remainingCash(new BigDecimal(cash))
        .remainingCashKrw(new BigDecimal(cashKrw)).currentFxRate(new BigDecimal(fx)).build();
    }

    // AAPL 은 매수 이력에서 가장 이른 BUY(매도는 빼고), MSFT 는 이력이 없어 보유 생성일을 매수일로 쓴다
    private void stubTwoHoldings(String aaplStatus, String msftStatus) throws JsonProcessingException {
      given(tradeServiceClientWrapper.getPortfolio(TEST_AUTHORIZATION)).willReturn(List.of(aapl, msft));
      given(tradeServiceClientWrapper.getTradeHistoryBySymbol(TEST_AUTHORIZATION, "AAPL")).willReturn(List.of(
        createTradeDto("BUY", LocalDate.of(2024, 3, 1)), createTradeDto("SELL", LocalDate.of(2023, 12, 1)),
        createTradeDto("BUY", LocalDate.of(2024, 1, 10))));
      given(tradeServiceClientWrapper.getTradeHistoryBySymbol(TEST_AUTHORIZATION, "MSFT")).willReturn(List.of());
      given(marketDataClientWrapper.getCurrentPrice(anyString())).willReturn(createCurrentPrice("ANY", new BigDecimal("200.00")));
      given(marketDataClientWrapper.getFxRate(anyString())).willReturn(new FxRateDto(LocalDate.of(2024, 1, 10), new BigDecimal("1300")));
      given(marketDataClientWrapper.getLatestFxRate()).willReturn(new FxRateDto(LocalDate.now(), new BigDecimal("1350")));
      given(responseMapper.toInvestmentResponse(eq(aapl), any(SimulationResponse.class))).willReturn(InvestmentResponse.builder()
        .symbol("AAPL").status(aaplStatus).simulation(sim(LocalDate.of(2024, 1, 10), LocalDate.of(2024, 6, 1),
          "1000000", "800", "1100000", "5", "1", "10", "13000", "1350")).build());
      given(responseMapper.toInvestmentResponse(eq(msft), any(SimulationResponse.class))).willReturn(InvestmentResponse.builder()
        .symbol("MSFT").status(msftStatus).simulation(sim(LocalDate.of(2024, 1, 15), LocalDate.of(2024, 6, 2),
          "2000000", "1500", "2000000", "3", "2", "20", "26000", "1360")).build());
      given(objectMapper.writeValueAsString(any())).willReturn("{}");
    }

    @Test
    @DisplayName("금액은 더하고, 매수일은 가장 이른 날 · 평가일과 환율은 가장 늦은 종목 것을 쓴다")
    void mergesTwoHoldings() throws JsonProcessingException {
      stubTwoHoldings("SUCCESS", "SUCCESS");

      InvestmentResponse result = investmentBacktestService.executeInvestment(testInvestmentRequest, TEST_AUTHORIZATION, TEST_EMAIL);

      SimulationResponse merged = result.getSimulation();
      assertThat(merged.getSymbol()).isEqualTo("AAPL, MSFT");
      assertThat(merged.getPurchaseDate()).isEqualTo(LocalDate.of(2024, 1, 10));
      assertThat(merged.getCurrentDate()).isEqualTo(LocalDate.of(2024, 6, 2));
      assertThat(merged.getCurrentFxRate()).isEqualByComparingTo("1360");
      assertThat(merged.getInvestmentAmount()).isEqualByComparingTo("3000000");
      assertThat(merged.getCurrentValue()).isEqualByComparingTo("2300");
      assertThat(merged.getCurrentValueKrw()).isEqualByComparingTo("3100000");
      assertThat(merged.getTotalDividends()).isEqualByComparingTo("8");
      assertThat(merged.getTradingFee()).isEqualByComparingTo("3");
      assertThat(merged.getRemainingCash()).isEqualByComparingTo("30");
      assertThat(merged.getRemainingCashKrw()).isEqualByComparingTo("39000");
      // 총자산 = 평가액 310만 + 남은 현금 3.9만, 수익 13.9만 / 투자 300만
      assertThat(merged.getTotalAssetKrw()).isEqualByComparingTo("3139000");
      assertThat(merged.getTotalReturnKrw()).isEqualByComparingTo("139000");
      assertThat(merged.getTotalReturnPercent()).isEqualByComparingTo("4.63");
      assertThat(result.getHoldingId()).isEqualTo("PORTFOLIO-2");
      assertThat(result.getStatus()).isEqualTo("SUCCESS");
      assertThat(result.getMessage()).isEqualTo("2개 종목 중 2개 성공");
      verify(objectMapper).writeValueAsString(result);
    }

    @Test
    @DisplayName("종목별 매수일은 가장 이른 BUY 거래일이고, 거래 이력이 없으면 보유 생성일이다")
    void earliestBuyDatePerHolding() throws JsonProcessingException {
      stubTwoHoldings("SUCCESS", "SUCCESS");

      investmentBacktestService.executeInvestment(testInvestmentRequest, TEST_AUTHORIZATION, TEST_EMAIL);

      assertThat(capturedSimulation(aapl).getPurchaseDate()).isEqualTo(LocalDate.of(2024, 1, 10));
      assertThat(capturedSimulation(msft).getPurchaseDate()).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    @DisplayName("실패한 종목이 있으면 부분 성공이다")
    void partialSuccess() throws JsonProcessingException {
      stubTwoHoldings("SUCCESS", "FAILED");

      InvestmentResponse result = investmentBacktestService.executeInvestment(testInvestmentRequest, TEST_AUTHORIZATION, TEST_EMAIL);

      assertThat(result.getStatus()).isEqualTo("PARTIAL_SUCCESS");
      assertThat(result.getMessage()).isEqualTo("2개 종목 중 1개 성공");
    }
  }

  @Nested
  @DisplayName("환율 대체 · 0 투자금")
  class FxFallbackTests {

    private final HoldingDto holding = createHoldingDto("AAPL", new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("1300000"));

    @Test
    @DisplayName("매수일 환율이 없으면 최신 환율을 매수 환율로 쓴다")
    void purchaseFxFallsBackToLatest() {
      stubSingleHolding(holding);
      given(marketDataClientWrapper.getFxRate(anyString())).willThrow(new RuntimeException("no fx"));
      given(marketDataClientWrapper.getLatestFxRate()).willReturn(new FxRateDto(LocalDate.now(), new BigDecimal("1350")));

      investmentBacktestService.executeInvestment(testInvestmentRequest, TEST_AUTHORIZATION, TEST_EMAIL);

      SimulationResponse sim = capturedSimulation(holding);
      assertThat(sim.getPurchaseFxRate()).isEqualByComparingTo("1350");
      assertThat(sim.getCurrentFxRate()).isEqualByComparingTo("1350");
    }

    @Test
    @DisplayName("매수일 환율도 최신 환율도 없으면 둘 다 기본 환율 1300 이다")
    void allFxFailUsesDefault() {
      stubSingleHolding(holding);
      given(marketDataClientWrapper.getFxRate(anyString())).willThrow(new RuntimeException("no fx"));
      given(marketDataClientWrapper.getLatestFxRate()).willThrow(new RuntimeException("no latest"));

      investmentBacktestService.executeInvestment(testInvestmentRequest, TEST_AUTHORIZATION, TEST_EMAIL);

      SimulationResponse sim = capturedSimulation(holding);
      assertThat(sim.getPurchaseFxRate()).isEqualByComparingTo("1300");
      assertThat(sim.getCurrentFxRate()).isEqualByComparingTo("1300");
    }

    @Test
    @DisplayName("매수일 환율은 있고 최신 환율만 없으면 현재 환율만 기본값이다")
    void onlyCurrentFxFails() {
      stubSingleHolding(holding);
      given(marketDataClientWrapper.getFxRate(anyString())).willReturn(new FxRateDto(LocalDate.of(2024, 1, 15), new BigDecimal("1200")));
      given(marketDataClientWrapper.getLatestFxRate()).willThrow(new RuntimeException("no latest"));

      investmentBacktestService.executeInvestment(testInvestmentRequest, TEST_AUTHORIZATION, TEST_EMAIL);

      SimulationResponse sim = capturedSimulation(holding);
      assertThat(sim.getPurchaseFxRate()).isEqualByComparingTo("1200");
      assertThat(sim.getCurrentFxRate()).isEqualByComparingTo("1300");
    }

    @Test
    @DisplayName("투자금과 평균 단가가 0 이면 수익률은 나누지 않고 0 이다")
    void zeroInvestment() {
      HoldingDto free = createHoldingDto("AAPL", new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO);
      stubSingleHolding(free);
      given(marketDataClientWrapper.getFxRate(anyString())).willReturn(new FxRateDto(LocalDate.of(2024, 1, 15), new BigDecimal("1300")));
      given(marketDataClientWrapper.getLatestFxRate()).willReturn(new FxRateDto(LocalDate.now(), new BigDecimal("1300")));

      investmentBacktestService.executeInvestment(testInvestmentRequest, TEST_AUTHORIZATION, TEST_EMAIL);

      SimulationResponse sim = capturedSimulation(free);
      assertThat(sim.getTotalReturnPercent()).isEqualByComparingTo("0");
      assertThat(sim.getStockReturnPercent()).isEqualByComparingTo("0");
    }
  }

  @Nested
  @DisplayName("결과 저장 · 예외 분류")
  class SaveAndErrorTests {

    private final HoldingDto holding = createHoldingDto("AAPL", new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("1300000"));

    private void stubFx() {
      given(marketDataClientWrapper.getFxRate(anyString())).willReturn(new FxRateDto(LocalDate.of(2024, 1, 15), new BigDecimal("1300")));
      given(marketDataClientWrapper.getLatestFxRate()).willReturn(new FxRateDto(LocalDate.now(), new BigDecimal("1300")));
    }

    @Test
    @DisplayName("이미 저장된 결과가 있으면 같은 행을 새 결과와 계산 시각으로 덮어쓴다")
    void updatesExistingResult() throws JsonProcessingException {
      stubSingleHolding(holding);
      stubFx();
      given(objectMapper.writeValueAsString(any())).willReturn("{\"new\":true}");
      given(investmentBacktestResultRepository.findByUserId(TEST_USER_ID)).willReturn(Optional.of(
        InvestmentBacktestResult.builder().id(7L).userId(TEST_USER_ID).resultData("{\"old\":true}")
          .calculatedAt(LocalDateTime.of(2024, 1, 1, 0, 0)).build()));

      investmentBacktestService.executeInvestment(testInvestmentRequest, TEST_AUTHORIZATION, TEST_EMAIL);

      ArgumentCaptor<InvestmentBacktestResult> saved = ArgumentCaptor.forClass(InvestmentBacktestResult.class);
      verify(investmentBacktestResultRepository).save(saved.capture());
      assertThat(saved.getValue().getId()).isEqualTo(7L);
      assertThat(saved.getValue().getResultData()).isEqualTo("{\"new\":true}");
      assertThat(saved.getValue().getCalculatedAt()).isAfter(LocalDateTime.of(2024, 1, 1, 0, 0));
    }

    @Test
    @DisplayName("결과를 JSON 으로 못 바꾸면 저장만 건너뛰고 결과는 돌려준다")
    void skipsSaveOnJsonError() throws JsonProcessingException {
      stubSingleHolding(holding);
      stubFx();
      given(objectMapper.writeValueAsString(any())).willThrow(new JsonProcessingException("boom") {});

      InvestmentResponse result = investmentBacktestService.executeInvestment(testInvestmentRequest, TEST_AUTHORIZATION, TEST_EMAIL);

      assertThat(result.getStatus()).isEqualTo("SUCCESS");
      verify(investmentBacktestResultRepository, never()).save(any());
      verify(backtestHistoryUtils).saveBacktestHistory(TEST_EMAIL, BacktestType.INVESTMENT_ANALYSIS, testInvestmentRequest);
    }

    @ParameterizedTest(name = "{0}")
    @NullSource
    @ValueSource(strings = {"환율 서버 응답 없음", "dividend feed down", "returned null"})
    @DisplayName("예상 못 한 예외는 메시지와 상관없이 계산 오류로 감싸고 원인을 붙인다")
    void wrapsUnexpectedErrors(String message) {
      IllegalStateException cause = new IllegalStateException(message);
      given(tradeServiceClientWrapper.getPortfolio(TEST_AUTHORIZATION)).willThrow(cause);

      assertThatThrownBy(() -> investmentBacktestService.executeInvestment(testInvestmentRequest, TEST_AUTHORIZATION, TEST_EMAIL))
        .isInstanceOf(BacktestException.class)
        .hasCause(cause)
        .extracting("code")
        .isEqualTo(BacktestResponse.CALCULATION_ERROR);
    }

    @Test
    @DisplayName("보유 종목 시세를 못 받으면 계산 오류로 덮지 않고 시세 서비스 사용 불가로 올린다")
    void marketDataOutagePassesThrough() {
      BacktestException outage = new BacktestException(BacktestResponse.MARKET_DATA_UNAVAILABLE);
      given(tradeServiceClientWrapper.getPortfolio(TEST_AUTHORIZATION)).willReturn(List.of(holding));
      given(tradeServiceClientWrapper.getTradeHistoryBySymbol(TEST_AUTHORIZATION, holding.symbol())).willReturn(List.of());
      given(marketDataClientWrapper.getCurrentPrice(holding.symbol())).willThrow(outage);

      assertThatThrownBy(() -> investmentBacktestService.executeInvestment(testInvestmentRequest, TEST_AUTHORIZATION, TEST_EMAIL))
        .isSameAs(outage);
    }
  }

  @Nested
  @DisplayName("받은 배당")
  class DividendsReceivedTests {

    // 현재가는 stubSingleHolding 의 200, 매수 환율 1300, 현재 환율 1400
    private void stubWithFx(HoldingDto holding) {
      stubSingleHolding(holding);
      given(marketDataClientWrapper.getFxRate(anyString()))
        .willReturn(new FxRateDto(LocalDate.of(2024, 1, 15), new BigDecimal("1300")));
      given(marketDataClientWrapper.getLatestFxRate()).willReturn(new FxRateDto(LocalDate.now(), new BigDecimal("1400")));
    }

    private HoldingDto holding(BigDecimal dividends) {
      return new HoldingDto("holding-123", "account-456", "AAPL", new BigDecimal("10"), new BigDecimal("120.00"),
        new BigDecimal("1800000"), dividends, null, LocalDateTime.of(2024, 1, 15, 10, 0));
    }

    @Test
    @DisplayName("지금 들고 있는 주식이 받은 배당을 남은 현금으로 넣어 총수익에 더한다")
    void addsDividendsReceived() {
      HoldingDto holding = holding(new BigDecimal("20.00"));
      stubWithFx(holding);

      investmentBacktestService.executeInvestment(testInvestmentRequest, TEST_AUTHORIZATION, TEST_EMAIL);

      // 평가액 10 × 200 × 1400 = 2,800,000 · 배당 20 × 1400 = 28,000 · 투자금 1,800,000
      SimulationResponse sim = capturedSimulation(holding);
      assertThat(sim.getTotalDividends()).isEqualByComparingTo("20.00");
      assertThat(sim.getRemainingCash()).isEqualByComparingTo("20.00");
      assertThat(sim.getRemainingCashKrw()).isEqualByComparingTo("28000");
      assertThat(sim.getTotalReturnKrw()).isEqualByComparingTo("1028000");
      assertThat(sim.getTotalReturnPercent()).isEqualByComparingTo("57.11");
      assertThat(sim.getDividendYield()).isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("받은 배당이 비어 오면 0 으로 계산한다")
    void noDividends_Zero() {
      HoldingDto holding = holding(null);
      stubWithFx(holding);

      investmentBacktestService.executeInvestment(testInvestmentRequest, TEST_AUTHORIZATION, TEST_EMAIL);

      SimulationResponse sim = capturedSimulation(holding);
      assertThat(sim.getTotalDividends()).isEqualByComparingTo("0");
      assertThat(sim.getRemainingCashKrw()).isEqualByComparingTo("0");
      assertThat(sim.getTotalReturnKrw()).isEqualByComparingTo("1000000");
      assertThat(sim.getTotalReturnPercent()).isEqualByComparingTo("55.56");
    }
  }
}
