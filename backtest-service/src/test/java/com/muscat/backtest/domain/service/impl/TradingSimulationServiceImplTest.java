package com.muscat.backtest.domain.service.impl;

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
import com.muscat.backtest.infra.client.MarketDataClient;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import com.muscat.backtest.infra.client.TradeServiceClientWrapper;
import com.muscat.backtest.infra.client.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

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
            OHLCPriceDto purchasePrice = createOHLCPrice(TEST_SYMBOL, testSimulationRequest.getPurchaseDate(),
                    new BigDecimal("180.00"), true);
            StockPriceDto currentPrice = createCurrentPrice(TEST_SYMBOL, new BigDecimal("230.00"));
            MarketDataClient.FxRate purchaseFxRate = new MarketDataClient.FxRate(
                    testSimulationRequest.getPurchaseDate(), new BigDecimal("1300.00"));
            MarketDataClient.FxRate currentFxRate = new MarketDataClient.FxRate(
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
            OHLCPriceDto purchasePrice = createOHLCPrice(TEST_SYMBOL, testSimulationRequest.getPurchaseDate(),
                    new BigDecimal("180.00"), true);
            StockPriceDto currentPrice = createCurrentPrice(TEST_SYMBOL, new BigDecimal("230.00"));
            MarketDataClient.FxRate purchaseFxRate = new MarketDataClient.FxRate(
                    testSimulationRequest.getPurchaseDate(), new BigDecimal("1300.00"));
            MarketDataClient.FxRate currentFxRate = new MarketDataClient.FxRate(
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
            SimulationResponse result = tradingSimulationService.runSimulation(testSimulationRequest, true);

            // then
            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(expectedResponse);
            verify(backtestHistoryUtils).saveBacktestHistory(TEST_USER_ID, BacktestType.COMPARISON,
                    testSimulationRequest);
            verify(marketDataClientWrapper).getOHLCPrice(TEST_SYMBOL, testSimulationRequest.getPurchaseDate().toString());
            verify(marketDataClientWrapper).getCurrentPrice(TEST_SYMBOL);
        }

        @Test
        @DisplayName("recordHistory=false일 때 히스토리가 기록되지 않는다")
        void runSimulation_WithoutRecordHistory_NoHistorySaved() {
            // given
            OHLCPriceDto purchasePrice = createOHLCPrice(TEST_SYMBOL, testSimulationRequest.getPurchaseDate(),
                    new BigDecimal("180.00"), true);
            StockPriceDto currentPrice = createCurrentPrice(TEST_SYMBOL, new BigDecimal("230.00"));
            MarketDataClient.FxRate purchaseFxRate = new MarketDataClient.FxRate(
                    testSimulationRequest.getPurchaseDate(), new BigDecimal("1300.00"));
            MarketDataClient.FxRate currentFxRate = new MarketDataClient.FxRate(
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
            SimulationResponse result = tradingSimulationService.runSimulation(testSimulationRequest, false);

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

            OHLCPriceDto purchasePrice = createOHLCPrice(TEST_SYMBOL, requestWithManualFxRates.getPurchaseDate(),
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
            SimulationResponse result = tradingSimulationService.runSimulation(requestWithManualFxRates, false);

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

            OHLCPriceDto purchasePrice = createOHLCPrice(TEST_SYMBOL, requestWithoutUserId.getPurchaseDate(),
                    new BigDecimal("180.00"), true);
            StockPriceDto currentPrice = createCurrentPrice(TEST_SYMBOL, new BigDecimal("230.00"));
            MarketDataClient.FxRate purchaseFxRate = new MarketDataClient.FxRate(
                    requestWithoutUserId.getPurchaseDate(), new BigDecimal("1300.00"));
            MarketDataClient.FxRate currentFxRate = new MarketDataClient.FxRate(
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
            SimulationResponse result = tradingSimulationService.runSimulation(requestWithoutUserId, true);

            // then
            assertThat(result).isNotNull();
            // userId가 null이므로 히스토리 저장 안 함
            verify(backtestHistoryUtils, never()).saveBacktestHistory(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("투자 실행 테스트")
    class ExecuteInvestmentTests {

        @Test
        @DisplayName("Holdings가 있을 때 투자 백테스트가 성공적으로 실행된다")
        void executeInvestment_WithHoldings_Success() throws JsonProcessingException {
            // given
            HoldingDto holding = createHoldingDto("AAPL", new BigDecimal("10.5"),
                    new BigDecimal("180.00"), new BigDecimal("1890000"));
            List<HoldingDto> holdings = List.of(holding);

            TradeDto trade = createTradeDto("BUY", LocalDate.of(2024, 1, 10));
            List<TradeDto> tradeHistory = List.of(trade);

            StockPriceDto currentPrice = createCurrentPrice("AAPL", new BigDecimal("230.00"));
            MarketDataClient.FxRate purchaseFxRate = new MarketDataClient.FxRate(
                    LocalDate.of(2024, 1, 10), new BigDecimal("1300.00"));
            MarketDataClient.FxRate currentFxRate = new MarketDataClient.FxRate(
                    LocalDate.now(), new BigDecimal("1320.00"));

            SimulationResponse simulationResponse = createSimulationResponse();
            InvestmentResponse investmentResponse = createInvestmentResponse(holding, simulationResponse);

            given(tradeServiceClientWrapper.getPortfolio(TEST_AUTHORIZATION))
                    .willReturn(holdings);
            given(tradeServiceClientWrapper.getTradeHistoryBySymbol(TEST_AUTHORIZATION, "AAPL"))
                    .willReturn(tradeHistory);
            given(marketDataClientWrapper.getCurrentPrice("AAPL"))
                    .willReturn(currentPrice);
            given(marketDataClientWrapper.getFxRate(anyString()))
                    .willReturn(purchaseFxRate);
            given(marketDataClientWrapper.getLatestFxRate())
                    .willReturn(currentFxRate);
            given(responseMapper.toInvestmentResponse(eq(holding), any(SimulationResponse.class)))
                    .willReturn(investmentResponse);
            given(objectMapper.writeValueAsString(any()))
                    .willReturn("{\"status\":\"SUCCESS\"}");

            // when
            InvestmentResponse result = tradingSimulationService.executeInvestment(
                    testInvestmentRequest, TEST_AUTHORIZATION);

            // then
            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(investmentResponse);
            verify(backtestHistoryUtils).saveBacktestHistory(TEST_USER_ID,
                    BacktestType.INVESTMENT_ANALYSIS, testInvestmentRequest);
            verify(investmentBacktestResultRepository).save(any(InvestmentBacktestResult.class));
        }

        @Test
        @DisplayName("Holdings가 없을 때 예외가 발생한다")
        void executeInvestment_NoHoldings_ThrowsException() {
            // given
            given(tradeServiceClientWrapper.getPortfolio(TEST_AUTHORIZATION))
                    .willReturn(Collections.emptyList());

            // when & then
            assertThatThrownBy(() ->
                    tradingSimulationService.executeInvestment(testInvestmentRequest, TEST_AUTHORIZATION))
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
            // given
            HoldingDto holding = createHoldingDto("AAPL", new BigDecimal("10.5"),
                    new BigDecimal("180.00"), new BigDecimal("1890000"));
            List<HoldingDto> holdings = List.of(holding);

            TradeDto trade = createTradeDto("BUY", LocalDate.of(2024, 1, 10));
            List<TradeDto> tradeHistory = List.of(trade);

            given(tradeServiceClientWrapper.getPortfolio(TEST_AUTHORIZATION))
                    .willReturn(holdings);
            given(tradeServiceClientWrapper.getTradeHistoryBySymbol(TEST_AUTHORIZATION, "AAPL"))
                    .willReturn(tradeHistory);
            given(marketDataClientWrapper.getCurrentPrice("AAPL"))
                    .willThrow(new RuntimeException("Calculation error occurred"));

            // when & then
            assertThatThrownBy(() ->
                    tradingSimulationService.executeInvestment(testInvestmentRequest, TEST_AUTHORIZATION))
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
            // given
            InvestmentBacktestResult cachedEntity = InvestmentBacktestResult.builder()
                    .resultId("result-123")
                    .userId(TEST_USER_ID)
                    .resultData("{\"status\":\"SUCCESS\"}")
                    .calculatedAt(LocalDateTime.now())
                    .build();

            InvestmentResponse expectedResponse = InvestmentResponse.builder()
                    .status("SUCCESS")
                    .build();

            given(investmentBacktestResultRepository.findByUserId(TEST_USER_ID))
                    .willReturn(Optional.of(cachedEntity));
            given(objectMapper.readValue(eq("{\"status\":\"SUCCESS\"}"), eq(InvestmentResponse.class)))
                    .willReturn(expectedResponse);

            // when
            Optional<InvestmentResponse> result = tradingSimulationService.getCachedInvestmentResult(TEST_USER_ID);

            // then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(expectedResponse);
            assertThat(result.get().getStatus()).isEqualTo("SUCCESS");
            verify(investmentBacktestResultRepository).findByUserId(TEST_USER_ID);
        }

        @Test
        @DisplayName("캐시된 투자 결과가 없을 때 Optional.empty가 반환된다")
        void getCachedInvestmentResult_NotFound_ReturnsEmpty() {
            // given
            given(investmentBacktestResultRepository.findByUserId(TEST_USER_ID))
                    .willReturn(Optional.empty());

            // when
            Optional<InvestmentResponse> result = tradingSimulationService.getCachedInvestmentResult(TEST_USER_ID);

            // then
            assertThat(result).isEmpty();
            verify(investmentBacktestResultRepository).findByUserId(TEST_USER_ID);
        }

        @Test
        @DisplayName("calculatedAt이 null일 때 Optional.empty가 반환된다")
        void getCachedInvestmentResult_NullCalculatedAt_ReturnsEmpty() {
            // given
            InvestmentBacktestResult cachedEntity = InvestmentBacktestResult.builder()
                    .resultId("result-123")
                    .userId(TEST_USER_ID)
                    .resultData("{\"status\":\"SUCCESS\"}")
                    .calculatedAt(null) // null calculatedAt
                    .build();

            given(investmentBacktestResultRepository.findByUserId(TEST_USER_ID))
                    .willReturn(Optional.of(cachedEntity));

            // when
            Optional<InvestmentResponse> result = tradingSimulationService.getCachedInvestmentResult(TEST_USER_ID);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("JSON 파싱 오류 시 Optional.empty가 반환된다")
        void getCachedInvestmentResult_JsonParsingError_ReturnsEmpty() throws JsonProcessingException {
            // given
            InvestmentBacktestResult cachedEntity = InvestmentBacktestResult.builder()
                    .resultId("result-123")
                    .userId(TEST_USER_ID)
                    .resultData("invalid json")
                    .calculatedAt(LocalDateTime.now())
                    .build();

            given(investmentBacktestResultRepository.findByUserId(TEST_USER_ID))
                    .willReturn(Optional.of(cachedEntity));
            given(objectMapper.readValue(eq("invalid json"), eq(InvestmentResponse.class)))
                    .willThrow(new JsonProcessingException("Invalid JSON") {});

            // when
            Optional<InvestmentResponse> result = tradingSimulationService.getCachedInvestmentResult(TEST_USER_ID);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Repository 조회 중 예외 발생 시 Optional.empty가 반환된다")
        void getCachedInvestmentResult_RepositoryError_ReturnsEmpty() {
            // given
            given(investmentBacktestResultRepository.findByUserId(TEST_USER_ID))
                    .willThrow(new RuntimeException("Database error"));

            // when
            Optional<InvestmentResponse> result = tradingSimulationService.getCachedInvestmentResult(TEST_USER_ID);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("캐시된 투자 결과 Entity 조회 테스트")
    class GetCachedInvestmentResultEntityTests {

        @Test
        @DisplayName("캐시된 투자 결과 Entity가 성공적으로 조회된다")
        void getCachedInvestmentResultEntity_Found_Success() {
            // given
            InvestmentBacktestResult cachedEntity = InvestmentBacktestResult.builder()
                    .resultId("result-123")
                    .userId(TEST_USER_ID)
                    .resultData("{\"status\":\"SUCCESS\"}")
                    .calculatedAt(LocalDateTime.now())
                    .build();

            given(investmentBacktestResultRepository.findByUserId(TEST_USER_ID))
                    .willReturn(Optional.of(cachedEntity));

            // when
            Optional<InvestmentBacktestResult> result = tradingSimulationService
                    .getCachedInvestmentResultEntity(TEST_USER_ID);

            // then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(cachedEntity);
            assertThat(result.get().getUserId()).isEqualTo(TEST_USER_ID);
            assertThat(result.get().getResultId()).isEqualTo("result-123");
        }

        @Test
        @DisplayName("캐시된 투자 결과 Entity가 없을 때 Optional.empty가 반환된다")
        void getCachedInvestmentResultEntity_NotFound_ReturnsEmpty() {
            // given
            given(investmentBacktestResultRepository.findByUserId(TEST_USER_ID))
                    .willReturn(Optional.empty());

            // when
            Optional<InvestmentBacktestResult> result = tradingSimulationService
                    .getCachedInvestmentResultEntity(TEST_USER_ID);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("calculatedAt이 null인 Entity는 반환되지 않는다")
        void getCachedInvestmentResultEntity_NullCalculatedAt_ReturnsEmpty() {
            // given
            InvestmentBacktestResult cachedEntity = InvestmentBacktestResult.builder()
                    .resultId("result-123")
                    .userId(TEST_USER_ID)
                    .resultData("{\"status\":\"SUCCESS\"}")
                    .calculatedAt(null)
                    .build();

            given(investmentBacktestResultRepository.findByUserId(TEST_USER_ID))
                    .willReturn(Optional.of(cachedEntity));

            // when
            Optional<InvestmentBacktestResult> result = tradingSimulationService
                    .getCachedInvestmentResultEntity(TEST_USER_ID);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Repository 조회 중 예외 발생 시 Optional.empty가 반환된다")
        void getCachedInvestmentResultEntity_RepositoryError_ReturnsEmpty() {
            // given
            given(investmentBacktestResultRepository.findByUserId(TEST_USER_ID))
                    .willThrow(new RuntimeException("Database error"));

            // when
            Optional<InvestmentBacktestResult> result = tradingSimulationService
                    .getCachedInvestmentResultEntity(TEST_USER_ID);

            // then
            assertThat(result).isEmpty();
        }
    }

    // === Helper Methods ===

    private OHLCPriceDto createOHLCPrice(String symbol, LocalDate date, BigDecimal closePrice,
                                         boolean available) {
        OHLCPriceDto dto = new OHLCPriceDto();
        dto.setSymbol(symbol);
        dto.setDate(date);
        dto.setOpenPrice(closePrice.subtract(new BigDecimal("5.00")));
        dto.setHighPrice(closePrice.add(new BigDecimal("10.00")));
        dto.setLowPrice(closePrice.subtract(new BigDecimal("8.00")));
        dto.setClosePrice(closePrice);
        dto.setVolume(1000000L);
        dto.setAvailable(available);
        return dto;
    }

    private StockPriceDto createCurrentPrice(String symbol, BigDecimal currentPrice) {
        StockPriceDto dto = new StockPriceDto();
        dto.setSymbol(symbol);
        dto.setCurrentPrice(currentPrice);
        dto.setPreviousClose(currentPrice.subtract(new BigDecimal("2.00")));
        dto.setChange(new BigDecimal("2.00"));
        dto.setChangePercent(new BigDecimal("0.87"));
        dto.setLastUpdated(LocalDateTime.now());
        dto.setAvailable(true);
        return dto;
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

    private HoldingDto createHoldingDto(String symbol, BigDecimal shares, BigDecimal avgPrice,
                                        BigDecimal totalInvested) {
        HoldingDto dto = new HoldingDto();
        dto.setHoldingId("holding-123");
        dto.setAccountId("account-456");
        dto.setSymbol(symbol);
        dto.setTotalQuantity(shares);
        dto.setAvgPurchasePrice(avgPrice);
        dto.setTotalInvestedAmount(totalInvested);
        dto.setTotalDividends(BigDecimal.ZERO);
        dto.setCreatedAt(LocalDateTime.of(2024, 1, 15, 10, 0));
        return dto;
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
                .simulation(simulation)
                .holdingId(holding.getHoldingId())
                .symbol(holding.getSymbol())
                .purchaseDate(holding.getPurchaseDate())
                .investmentAmount(holding.getTotalInvested())
                .purchasePrice(holding.getAveragePrice())
                .shares(holding.getShares())
                .status("SUCCESS")
                .message("투자 백테스트가 성공적으로 완료되었습니다")
                .portfolioCreated(true)
                .portfolioStatus("ACTIVE")
                .build();
    }
}
