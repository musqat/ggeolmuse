package com.muscat.backtest.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;

import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.enums.type.BacktestType;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.util.BacktestHistoryUtils;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.request.OptimalTimingRequest;
import com.muscat.backtest.domain.dto.request.StrategyComparisonRequest;
import com.muscat.backtest.domain.dto.request.SymbolComparisonRequest;
import com.muscat.backtest.domain.dto.request.TimingComparisonRequest;
import com.muscat.backtest.domain.dto.response.ComparisonResponse;
import com.muscat.backtest.domain.dto.response.OptimalTimingResponse;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.ComparisonItem;
import com.muscat.backtest.domain.model.StrategyParameter;
import com.muscat.backtest.domain.service.TradingSimulationService;
import com.muscat.backtest.domain.strategy.ConditionalPurchaseStrategy;
import com.muscat.backtest.domain.strategy.DCAStrategy;
import com.muscat.backtest.domain.strategy.InvestmentStrategy;
import com.muscat.backtest.domain.strategy.OptimalTimingStrategy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BacktestAnalysisService 테스트")
class BacktestAnalysisServiceImplTest {

  @Mock
  private TradingSimulationService tradingSimulationService;

  @Mock
  private DCAStrategy dcaStrategy;

  @Mock
  private ConditionalPurchaseStrategy conditionalPurchaseStrategy;

  @Mock
  private ResponseMapper responseMapper;

  @Mock
  private BacktestHistoryUtils backtestHistoryUtils;

  @Mock
  private OptimalTimingStrategy optimalTimingStrategy;

  private BacktestAnalysisServiceImpl backtestAnalysisService;

  @BeforeEach
  void setUp() {
    given(dcaStrategy.getStrategyType()).willReturn(StrategyType.DCA);
    given(conditionalPurchaseStrategy.getStrategyType()).willReturn(
      StrategyType.CONDITIONAL_PURCHASE);

    List<InvestmentStrategy> strategies = Arrays.asList(dcaStrategy, conditionalPurchaseStrategy);

    backtestAnalysisService = new BacktestAnalysisServiceImpl(
      tradingSimulationService,
      strategies,
      responseMapper,
      backtestHistoryUtils,
      optimalTimingStrategy
    );
  }

  @Nested
  @DisplayName("DCA 전략 테스트")
  class DcaStrategyTests {

    @Test
    @DisplayName("DCA 전략 실행 성공")
    void runDcaStrategy_Success() {
      // Given
      DcaStrategyRequest request = DcaStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2024, 1, 1))
        .monthlyAmount(BigDecimal.valueOf(1000))
        .purchaseDay(1)
        .userId("user123")
        .build();

      StrategyResponse expectedResponse = StrategyResponse.builder()
        .symbol("AAPL")
        .totalInvested(BigDecimal.valueOf(12000))
        .totalReturnPercent(BigDecimal.valueOf(15.5))
        .build();

      given(dcaStrategy.executeDca(request)).willReturn(expectedResponse);
      willDoNothing().given(backtestHistoryUtils)
        .saveBacktestHistory(any(), any(BacktestType.class), any());

      // When
      StrategyResponse result = backtestAnalysisService.runDcaStrategy(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSymbol()).isEqualTo("AAPL");
      assertThat(result.getTotalReturnPercent()).isEqualByComparingTo(BigDecimal.valueOf(15.5));
      verify(dcaStrategy).executeDca(request);
      verify(backtestHistoryUtils).saveBacktestHistory(
        eq("user123"),
        eq(BacktestType.STRATEGY_SIMULATION),
        eq(request)
      );
    }

    @Test
    @DisplayName("DCA 전략 실행 실패 - 전략 실행 오류")
    void runDcaStrategy_StrategyExecutionFailure() {
      // Given
      DcaStrategyRequest request = DcaStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2024, 1, 1))
        .monthlyAmount(BigDecimal.valueOf(1000))
        .userId("user123")
        .build();

      given(dcaStrategy.executeDca(request))
        .willThrow(new BacktestException(BacktestResponse.DATA_NOT_FOUND, "데이터 없음"));

      // When & Then
      assertThatThrownBy(() -> backtestAnalysisService.runDcaStrategy(request))
        .isInstanceOf(BacktestException.class)
        .hasMessageContaining("데이터 없음");
    }

    @Test
    @DisplayName("히스토리 저장 실패해도 전략 실행 성공")
    void runDcaStrategy_HistorySaveFailure_StrategyStillSucceeds() {
      // Given
      DcaStrategyRequest request = DcaStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2024, 1, 1))
        .monthlyAmount(BigDecimal.valueOf(1000))
        .userId("user123")
        .build();

      StrategyResponse expectedResponse = StrategyResponse.builder()
        .symbol("AAPL")
        .totalReturnPercent(BigDecimal.valueOf(15.5))
        .build();

      given(dcaStrategy.executeDca(request)).willReturn(expectedResponse);
      willThrow(new RuntimeException("DB 연결 실패"))
        .given(backtestHistoryUtils)
        .saveBacktestHistory(any(), any(BacktestType.class), any());

      // When
      StrategyResponse result = backtestAnalysisService.runDcaStrategy(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getTotalReturnPercent()).isEqualByComparingTo(BigDecimal.valueOf(15.5));
    }
  }

  @Nested
  @DisplayName("조건부 매수 전략 테스트")
  class ConditionalStrategyTests {

    @Test
    @DisplayName("조건부 매수 전략 실행 성공")
    void runConditionalStrategy_Success() {
      // Given
      ConditionalStrategyRequest request = ConditionalStrategyRequest.builder()
        .symbol("TSLA")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2024, 1, 1))
        .totalInvestment(BigDecimal.valueOf(10000))
        .dropPercentage(BigDecimal.valueOf(10))
        .maxPurchases(5)
        .userId("user456")
        .build();

      StrategyResponse expectedResponse = StrategyResponse.builder()
        .symbol("TSLA")
        .totalInvested(BigDecimal.valueOf(8000))
        .totalReturnPercent(BigDecimal.valueOf(25.0))
        .build();

      given(conditionalPurchaseStrategy.executeConditional(request))
        .willReturn(expectedResponse);

      // When
      StrategyResponse result = backtestAnalysisService.runConditionalStrategy(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSymbol()).isEqualTo("TSLA");
      assertThat(result.getTotalReturnPercent()).isEqualByComparingTo(BigDecimal.valueOf(25.0));
      verify(conditionalPurchaseStrategy).executeConditional(request);
      verify(backtestHistoryUtils).saveBacktestHistory(
        eq("user456"),
        eq(BacktestType.STRATEGY_SIMULATION),
        eq(request)
      );
    }
  }

  @Nested
  @DisplayName("종목 비교 테스트")
  class CompareSymbolsTests {

    @Test
    @DisplayName("여러 종목 비교 성공")
    void compareSymbols_Success() {
      // Given
      SymbolComparisonRequest request = new SymbolComparisonRequest();
      request.setSymbols(Arrays.asList("AAPL", "GOOGL", "MSFT"));
      request.setStartDate(LocalDate.of(2023, 1, 1));
      request.setInvestmentAmount(BigDecimal.valueOf(10000));
      request.setUserId("user123");

      SimulationResponse aaplResponse = SimulationResponse.builder()
        .symbol("AAPL")
        .totalReturnPercent(BigDecimal.valueOf(20))
        .build();

      SimulationResponse googlResponse = SimulationResponse.builder()
        .symbol("GOOGL")
        .totalReturnPercent(BigDecimal.valueOf(15))
        .build();

      SimulationResponse msftResponse = SimulationResponse.builder()
        .symbol("MSFT")
        .totalReturnPercent(BigDecimal.valueOf(25))
        .build();

      ComparisonItem aaplItem = ComparisonItem.builder()
        .name("AAPL")
        .totalReturnPercent(BigDecimal.valueOf(20))
        .build();

      ComparisonItem googlItem = ComparisonItem.builder()
        .name("GOOGL")
        .totalReturnPercent(BigDecimal.valueOf(15))
        .build();

      ComparisonItem msftItem = ComparisonItem.builder()
        .name("MSFT")
        .totalReturnPercent(BigDecimal.valueOf(25))
        .build();

      given(tradingSimulationService.runSimulation(any(), eq(false)))
        .willReturn(aaplResponse, googlResponse, msftResponse);

      given(responseMapper.toComparisonItemFromSimulation(aaplResponse, "AAPL"))
        .willReturn(aaplItem);
      given(responseMapper.toComparisonItemFromSimulation(googlResponse, "GOOGL"))
        .willReturn(googlItem);
      given(responseMapper.toComparisonItemFromSimulation(msftResponse, "MSFT"))
        .willReturn(msftItem);

      ComparisonResponse expectedResponse = ComparisonResponse.builder()
        .bestPerformer(msftItem)
        .worstPerformer(googlItem)
        .build();

      given(responseMapper.toComparisonResponse(eq(request), any(), any()))
        .willReturn(expectedResponse);

      // When
      ComparisonResponse result = backtestAnalysisService.compareSymbols(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getBestPerformer()).isNotNull();
      assertThat(result.getBestPerformer().getName()).isEqualTo("MSFT");
      verify(tradingSimulationService, times(3)).runSimulation(any(), eq(false));
      verify(backtestHistoryUtils).saveBacktestHistory(
        eq("user123"),
        eq(BacktestType.COMPARISON),
        eq(request)
      );
    }

    @Test
    @DisplayName("모든 종목 시뮬레이션 실패 시 예외 발생")
    void compareSymbols_AllSymbolsFailure_ThrowsException() {
      // Given
      SymbolComparisonRequest request = new SymbolComparisonRequest();
      request.setSymbols(Arrays.asList("INVALID1", "INVALID2"));
      request.setStartDate(LocalDate.of(2023, 1, 1));
      request.setInvestmentAmount(BigDecimal.valueOf(10000));
      request.setUserId("user123");

      given(tradingSimulationService.runSimulation(any(), eq(false)))
        .willThrow(new BacktestException(BacktestResponse.DATA_NOT_FOUND, "데이터 없음"));

      // When & Then
      assertThatThrownBy(() -> backtestAnalysisService.compareSymbols(request))
        .isInstanceOf(BacktestException.class)
        .hasMessageContaining("모든 종목의 데이터를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("일부 종목만 성공해도 비교 가능")
    void compareSymbols_PartialSuccess() {
      // Given
      SymbolComparisonRequest request = new SymbolComparisonRequest();
      request.setSymbols(Arrays.asList("AAPL", "INVALID", "MSFT"));
      request.setStartDate(LocalDate.of(2023, 1, 1));
      request.setInvestmentAmount(BigDecimal.valueOf(10000));
      request.setUserId("user123");

      SimulationResponse aaplResponse = SimulationResponse.builder()
        .symbol("AAPL")
        .totalReturnPercent(BigDecimal.valueOf(20))
        .build();

      SimulationResponse msftResponse = SimulationResponse.builder()
        .symbol("MSFT")
        .totalReturnPercent(BigDecimal.valueOf(25))
        .build();

      ComparisonItem aaplItem = ComparisonItem.builder()
        .name("AAPL")
        .totalReturnPercent(BigDecimal.valueOf(20))
        .build();

      ComparisonItem msftItem = ComparisonItem.builder()
        .name("MSFT")
        .totalReturnPercent(BigDecimal.valueOf(25))
        .build();

      given(tradingSimulationService.runSimulation(any(), eq(false)))
        .willReturn(aaplResponse)
        .willThrow(new BacktestException(BacktestResponse.DATA_NOT_FOUND, "데이터 없음"))
        .willReturn(msftResponse);

      given(responseMapper.toComparisonItemFromSimulation(aaplResponse, "AAPL"))
        .willReturn(aaplItem);
      given(responseMapper.toComparisonItemFromSimulation(msftResponse, "MSFT"))
        .willReturn(msftItem);

      ComparisonResponse expectedResponse = ComparisonResponse.builder()
        .bestPerformer(msftItem)
        .worstPerformer(aaplItem)
        .build();

      given(responseMapper.toComparisonResponse(eq(request), any(), any()))
        .willReturn(expectedResponse);

      // When
      ComparisonResponse result = backtestAnalysisService.compareSymbols(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getBestPerformer().getName()).isEqualTo("MSFT");
    }
  }

  @Nested
  @DisplayName("전략 비교 테스트")
  class CompareStrategiesTests {

    @Test
    @DisplayName("DCA와 조건부 매수 전략 비교 성공")
    void compareStrategies_DcaAndConditional_Success() {
      // Given
      StrategyParameter dcaConfig = new StrategyParameter();
      dcaConfig.setStrategyType(StrategyType.DCA);
      dcaConfig.setName("DCA 전략");
      dcaConfig.setMonthlyAmount(BigDecimal.valueOf(1000));
      dcaConfig.setPurchaseDay(1);

      StrategyParameter conditionalConfig = new StrategyParameter();
      conditionalConfig.setStrategyType(StrategyType.CONDITIONAL_PURCHASE);
      conditionalConfig.setName("조건부 매수");
      conditionalConfig.setTotalInvestment(BigDecimal.valueOf(12000));
      conditionalConfig.setDropPercentage(BigDecimal.valueOf(10));
      conditionalConfig.setMaxPurchases(5);

      StrategyComparisonRequest request = new StrategyComparisonRequest();
      request.setSymbol("AAPL");
      request.setStartDate(LocalDate.of(2023, 1, 1));
      request.setEndDate(LocalDate.of(2024, 1, 1));
      request.setStrategies(Arrays.asList(dcaConfig, conditionalConfig));
      request.setUserId("user123");

      StrategyResponse dcaResponse = StrategyResponse.builder()
        .symbol("AAPL")
        .totalReturnPercent(BigDecimal.valueOf(15))
        .build();

      StrategyResponse conditionalResponse = StrategyResponse.builder()
        .symbol("AAPL")
        .totalReturnPercent(BigDecimal.valueOf(20))
        .build();

      ComparisonItem dcaItem = ComparisonItem.builder()
        .name("DCA 전략")
        .totalReturnPercent(BigDecimal.valueOf(15))
        .build();

      ComparisonItem conditionalItem = ComparisonItem.builder()
        .name("조건부 매수")
        .totalReturnPercent(BigDecimal.valueOf(20))
        .build();

      given(dcaStrategy.executeDca(any())).willReturn(dcaResponse);
      given(conditionalPurchaseStrategy.executeConditional(any())).willReturn(conditionalResponse);
      given(responseMapper.toComparisonItemFromStrategy(dcaResponse, "DCA 전략"))
        .willReturn(dcaItem);
      given(responseMapper.toComparisonItemFromStrategy(conditionalResponse, "조건부 매수"))
        .willReturn(conditionalItem);

      ComparisonResponse expectedResponse = ComparisonResponse.builder()
        .bestPerformer(conditionalItem)
        .worstPerformer(dcaItem)
        .build();

      given(responseMapper.toComparisonResponse(eq(request), any(), any()))
        .willReturn(expectedResponse);

      // When
      ComparisonResponse result = backtestAnalysisService.compareStrategies(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getBestPerformer().getName()).isEqualTo("조건부 매수");
      verify(dcaStrategy).executeDca(any());
      verify(conditionalPurchaseStrategy).executeConditional(any());
    }

    @Test
    @DisplayName("전략 타입이 없는 경우 일반 시뮬레이션 실행")
    void compareStrategies_NoStrategyType_RunsSimulation() {
      // Given
      StrategyParameter customConfig = new StrategyParameter();
      customConfig.setName("커스텀 전략");
      customConfig.setPurchaseDate(LocalDate.of(2023, 6, 1));

      StrategyComparisonRequest request = new StrategyComparisonRequest();
      request.setSymbol("AAPL");
      request.setStartDate(LocalDate.of(2023, 1, 1));
      request.setEndDate(LocalDate.of(2024, 1, 1));
      request.setInvestmentAmount(BigDecimal.valueOf(10000));
      request.setStrategies(Collections.singletonList(customConfig));
      request.setUserId("user123");

      SimulationResponse simulationResponse = SimulationResponse.builder()
        .symbol("AAPL")
        .totalReturnPercent(BigDecimal.valueOf(18))
        .build();

      ComparisonItem customItem = ComparisonItem.builder()
        .name("커스텀 전략")
        .totalReturnPercent(BigDecimal.valueOf(18))
        .build();

      given(tradingSimulationService.runSimulation(any(), eq(false)))
        .willReturn(simulationResponse);
      given(responseMapper.toComparisonItemFromSimulation(simulationResponse, "커스텀 전략"))
        .willReturn(customItem);

      ComparisonResponse expectedResponse = ComparisonResponse.builder()
        .bestPerformer(customItem)
        .build();

      given(responseMapper.toComparisonResponse(eq(request), any(), any()))
        .willReturn(expectedResponse);

      // When
      ComparisonResponse result = backtestAnalysisService.compareStrategies(request);

      // Then
      assertThat(result).isNotNull();
      verify(tradingSimulationService).runSimulation(any(), eq(false));
    }
  }

  @Nested
  @DisplayName("타이밍 비교 테스트")
  class CompareTimingTests {

    @Test
    @DisplayName("여러 매수 시점 비교 성공")
    void compareTiming_Success() {
      // Given
      LocalDate timing1 = LocalDate.of(2023, 1, 1);
      LocalDate timing2 = LocalDate.of(2023, 6, 1);
      LocalDate timing3 = LocalDate.of(2023, 12, 1);

      TimingComparisonRequest request = new TimingComparisonRequest();
      request.setSymbol("AAPL");
      request.setPurchaseDates(Arrays.asList(timing1, timing2, timing3));
      request.setInvestmentAmount(BigDecimal.valueOf(10000));
      request.setUserId("user123");

      SimulationResponse response1 = SimulationResponse.builder()
        .symbol("AAPL")
        .totalReturnPercent(BigDecimal.valueOf(10))
        .build();

      SimulationResponse response2 = SimulationResponse.builder()
        .symbol("AAPL")
        .totalReturnPercent(BigDecimal.valueOf(15))
        .build();

      SimulationResponse response3 = SimulationResponse.builder()
        .symbol("AAPL")
        .totalReturnPercent(BigDecimal.valueOf(5))
        .build();

      ComparisonItem item1 = ComparisonItem.builder()
        .name("2023-01-01 매수")
        .totalReturnPercent(BigDecimal.valueOf(10))
        .build();

      ComparisonItem item2 = ComparisonItem.builder()
        .name("2023-06-01 매수")
        .totalReturnPercent(BigDecimal.valueOf(15))
        .build();

      ComparisonItem item3 = ComparisonItem.builder()
        .name("2023-12-01 매수")
        .totalReturnPercent(BigDecimal.valueOf(5))
        .build();

      given(tradingSimulationService.runSimulation(any(), eq(false)))
        .willReturn(response1, response2, response3);

      given(responseMapper.toComparisonItemFromSimulation(any(), any()))
        .willReturn(item1, item2, item3);

      ComparisonResponse expectedResponse = ComparisonResponse.builder()
        .bestPerformer(item2)
        .worstPerformer(item3)
        .build();

      given(responseMapper.toComparisonResponse(eq(request), any(), any()))
        .willReturn(expectedResponse);

      // When
      ComparisonResponse result = backtestAnalysisService.compareTiming(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getBestPerformer().getName()).isEqualTo("2023-06-01 매수");
      assertThat(result.getWorstPerformer().getName()).isEqualTo("2023-12-01 매수");
      verify(tradingSimulationService, times(3)).runSimulation(any(), eq(false));
    }
  }

  @Nested
  @DisplayName("최적 타이밍 분석 테스트")
  class AnalyzeOptimalTimingTests {

    @Test
    @DisplayName("최적 타이밍 분석 성공")
    void analyzeOptimalTiming_Success() {
      // Given
      OptimalTimingRequest request = OptimalTimingRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2024, 1, 1))
        .targetReturnPercent(BigDecimal.valueOf(10))
        .investmentAmount(BigDecimal.valueOf(10000))
        .userId("user123")
        .build();

      OptimalTimingResponse expectedResponse = OptimalTimingResponse.builder()
        .symbol("AAPL")
        .totalAnalyzedDays(365)
        .totalQualifyingDays(200)
        .build();

      given(optimalTimingStrategy.analyzeOptimalTiming(request)).willReturn(expectedResponse);

      // When
      OptimalTimingResponse result = backtestAnalysisService.analyzeOptimalTiming(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSymbol()).isEqualTo("AAPL");
      assertThat(result.getTotalAnalyzedDays()).isEqualTo(365);
      assertThat(result.getTotalQualifyingDays()).isEqualTo(200);
      verify(optimalTimingStrategy).analyzeOptimalTiming(request);
      verify(backtestHistoryUtils).saveBacktestHistory(
        eq("user123"),
        eq(BacktestType.STRATEGY_SIMULATION),
        eq(request)
      );
    }

    @Test
    @DisplayName("userId 없어도 분석 성공 (히스토리 저장 안함)")
    void analyzeOptimalTiming_NoUserId_Success() {
      // Given
      OptimalTimingRequest request = OptimalTimingRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2024, 1, 1))
        .targetReturnPercent(BigDecimal.valueOf(10))
        .investmentAmount(BigDecimal.valueOf(10000))
        .userId(null)
        .build();

      OptimalTimingResponse expectedResponse = OptimalTimingResponse.builder()
        .symbol("AAPL")
        .totalAnalyzedDays(365)
        .totalQualifyingDays(200)
        .build();

      given(optimalTimingStrategy.analyzeOptimalTiming(request)).willReturn(expectedResponse);

      // When
      OptimalTimingResponse result = backtestAnalysisService.analyzeOptimalTiming(request);

      // Then
      assertThat(result).isNotNull();
      verify(optimalTimingStrategy).analyzeOptimalTiming(request);
      verify(backtestHistoryUtils, never()).saveBacktestHistory(any(), any(), any());
    }
  }
}
