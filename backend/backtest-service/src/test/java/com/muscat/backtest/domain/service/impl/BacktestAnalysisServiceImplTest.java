package com.muscat.backtest.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;

import com.muscat.backtest.common.calculation.CapitalGainsTaxCalculator;
import com.muscat.backtest.common.engine.PortfolioEngine;
import com.muscat.backtest.common.engine.StrategyPerformance;
import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.BacktestType;
import com.muscat.backtest.common.enums.type.DcaFrequency;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.enums.type.PortfolioStrategyType;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.util.BacktestHistoryUtils;
import com.muscat.backtest.domain.dto.request.BaseComparisonRequest;
import com.muscat.backtest.common.enums.type.PriceDirection;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.request.MaBandStrategyRequest;
import com.muscat.backtest.domain.dto.request.MaCrossStrategyRequest;
import com.muscat.backtest.domain.dto.request.PortfolioAsset;
import com.muscat.backtest.domain.dto.request.PortfolioRequest;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.request.StrategyComparisonRequest;
import com.muscat.backtest.domain.dto.request.SymbolComparisonRequest;
import com.muscat.backtest.domain.dto.response.CapitalGainsTax;
import com.muscat.backtest.domain.dto.response.ComparisonResponse;
import com.muscat.backtest.domain.dto.response.PortfolioResponse;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.ComparisonItem;
import com.muscat.backtest.domain.model.StrategyParameter;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.common.enums.type.TradeReason;
import com.muscat.backtest.domain.service.TradingSimulationService;
import com.muscat.backtest.domain.strategy.ConditionalPurchaseStrategy;
import com.muscat.backtest.domain.strategy.DCAStrategy;
import com.muscat.backtest.domain.strategy.InvestmentStrategy;
import com.muscat.backtest.domain.strategy.MaBandStrategy;
import com.muscat.backtest.domain.strategy.MaCrossStrategy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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
  private MaCrossStrategy maCrossStrategy;

  @Mock
  private MaBandStrategy maBandStrategy;

  @Mock
  private ResponseMapper responseMapper;

  @Mock
  private BacktestHistoryUtils backtestHistoryUtils;

  @Mock
  private PortfolioEngine portfolioEngine;

  private BacktestAnalysisServiceImpl backtestAnalysisService;

  @BeforeEach
  void setUp() {
    given(dcaStrategy.getStrategyType()).willReturn(StrategyType.DCA);
    given(conditionalPurchaseStrategy.getStrategyType()).willReturn(
      StrategyType.CONDITIONAL_PURCHASE);
    given(maCrossStrategy.getStrategyType()).willReturn(StrategyType.MA_CROSS);
    given(maBandStrategy.getStrategyType()).willReturn(StrategyType.MA_BAND);

    List<InvestmentStrategy> strategies = Arrays.asList(dcaStrategy, conditionalPurchaseStrategy, maCrossStrategy,
      maBandStrategy);

    backtestAnalysisService = new BacktestAnalysisServiceImpl(
      tradingSimulationService,
      strategies,
      responseMapper,
      backtestHistoryUtils,
      portfolioEngine
    );
  }

  @Nested
  @DisplayName("포트폴리오")
  class PortfolioTests {

    private PortfolioRequest request(String... symbols) {
      return PortfolioRequest.builder().userId("user123").startDate(LocalDate.of(2024, 1, 1))
        .endDate(LocalDate.of(2024, 12, 31))
        .assets(Arrays.stream(symbols)
          .map(symbol -> new PortfolioAsset(symbol, BigDecimal.valueOf(1000000), null, null, null, null, null))
          .toList())
        .build();
    }

    @Test
    @DisplayName("요청을 검사한 뒤 엔진 결과로 응답을 만들고 투자 전략 시뮬레이션 이력에 요청을 남긴다")
    void runsAndSavesHistory() {
      PortfolioRequest request = request("SPY");
      LocalDate start = LocalDate.of(2024, 1, 2);
      given(portfolioEngine.run(request)).willReturn(new PortfolioEngine.Result(start, request.getEndDate(), List.of(),
        List.of(), List.of(new PortfolioEngine.Position("SPY", PortfolioStrategyType.LUMP_SUM, BigDecimal.valueOf(1000000),
          BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO)), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.valueOf(1300), new StrategyPerformance.Result(List.of(), null, null),
        CapitalGainsTaxCalculator.Payments.NONE, List.of()));

      PortfolioResponse response = backtestAnalysisService.runPortfolio(request);

      assertThat(response.startDate()).isEqualTo(start);
      assertThat(response.assets()).extracting(PortfolioResponse.Asset::symbol).containsExactly("SPY");
      verify(backtestHistoryUtils).saveBacktestHistory("user123", BacktestType.STRATEGY_SIMULATION, request);
    }

    @Test
    @DisplayName("같은 종목이 두 번 있으면 엔진을 돌리지 않고 이력도 남기지 않는다")
    void invalidRequestStopsBeforeEngine() {
      assertThatThrownBy(() -> backtestAnalysisService.runPortfolio(request("SPY", "spy")))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.INVALID_PORTFOLIO);

      verify(portfolioEngine, never()).run(any());
      verify(backtestHistoryUtils, never()).saveBacktestHistory(any(), any(), any());
    }
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
        .amountPerPurchase(BigDecimal.valueOf(1000))
        .purchaseDay(1)
        .userId("user123")
        .build();

      StrategyResponse expectedResponse = StrategyResponse.builder()
        .symbol("AAPL")
        .totalInvested(BigDecimal.valueOf(12000))
        .totalReturnPercent(BigDecimal.valueOf(15.5))
        .build();

      given(dcaStrategy.execute(request)).willReturn(expectedResponse);
      willDoNothing().given(backtestHistoryUtils)
        .saveBacktestHistory(any(), any(BacktestType.class), any());

      // When
      StrategyResponse result = backtestAnalysisService.runDcaStrategy(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSymbol()).isEqualTo("AAPL");
      assertThat(result.getTotalReturnPercent()).isEqualByComparingTo(BigDecimal.valueOf(15.5));
      verify(dcaStrategy).execute(request);
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
        .amountPerPurchase(BigDecimal.valueOf(1000))
        .userId("user123")
        .build();

      given(dcaStrategy.execute(request))
        .willThrow(new BacktestException(BacktestResponse.DATA_NOT_FOUND, "데이터 없음"));

      // When & Then
      assertThatThrownBy(() -> backtestAnalysisService.runDcaStrategy(request))
        .isInstanceOf(BacktestException.class)
        .hasMessageContaining("데이터 없음");
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

      given(conditionalPurchaseStrategy.execute(request))
        .willReturn(expectedResponse);

      // When
      StrategyResponse result = backtestAnalysisService.runConditionalStrategy(request);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSymbol()).isEqualTo("TSLA");
      assertThat(result.getTotalReturnPercent()).isEqualByComparingTo(BigDecimal.valueOf(25.0));
      verify(conditionalPurchaseStrategy).execute(request);
      verify(backtestHistoryUtils).saveBacktestHistory(
        eq("user456"),
        eq(BacktestType.STRATEGY_SIMULATION),
        eq(request)
      );
    }
  }

  @Nested
  @DisplayName("이동평균 교차 전략 테스트")
  class MaCrossStrategyTests {

    @Test
    @DisplayName("이동평균 교차 전략을 실행하고 전략 실행 이력을 남긴다")
    void runMaCrossStrategy_Success() {
      MaCrossStrategyRequest request = MaCrossStrategyRequest.builder()
        .symbol("AAPL")
        .startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2024, 1, 1))
        .investmentAmount(BigDecimal.valueOf(10_000_000))
        .userId("user789")
        .build();
      StrategyResponse expectedResponse = StrategyResponse.builder()
        .symbol("AAPL")
        .strategyType(StrategyType.MA_CROSS)
        .build();
      given(maCrossStrategy.execute(request)).willReturn(expectedResponse);

      StrategyResponse result = backtestAnalysisService.runMaCrossStrategy(request);

      assertThat(result).isSameAs(expectedResponse);
      verify(backtestHistoryUtils).saveBacktestHistory(
        eq("user789"),
        eq(BacktestType.STRATEGY_SIMULATION),
        eq(request)
      );
    }
  }

  @Nested
  @DisplayName("이동평균 밴드 전략 테스트")
  class MaBandStrategyTests {

    @Test
    @DisplayName("이동평균 밴드 전략을 실행하고 전략 실행 이력을 남긴다")
    void runMaBandStrategy_Success() {
      MaBandStrategyRequest request = MaBandStrategyRequest.builder()
        .symbol("SPY")
        .startDate(LocalDate.of(2015, 1, 1))
        .endDate(LocalDate.of(2024, 12, 31))
        .investmentAmount(BigDecimal.valueOf(10_000_000))
        .userId("user789")
        .build();
      StrategyResponse expectedResponse = StrategyResponse.builder()
        .symbol("SPY")
        .strategyType(StrategyType.MA_BAND)
        .build();
      given(maBandStrategy.execute(request)).willReturn(expectedResponse);

      StrategyResponse result = backtestAnalysisService.runMaBandStrategy(request);

      assertThat(result).isSameAs(expectedResponse);
      verify(backtestHistoryUtils).saveBacktestHistory("user789", BacktestType.STRATEGY_SIMULATION, request);
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
      dcaConfig.setAmountPerPurchase(BigDecimal.valueOf(1000));
      dcaConfig.setPurchaseDay(1);

      StrategyParameter conditionalConfig = new StrategyParameter();
      conditionalConfig.setStrategyType(StrategyType.CONDITIONAL_PURCHASE);
      conditionalConfig.setName("조건부 매수");
      conditionalConfig.setAmountPerPurchase(BigDecimal.valueOf(2000));
      conditionalConfig.setDropPercentage(BigDecimal.valueOf(10));
      conditionalConfig.setMaxPurchases(5);

      StrategyComparisonRequest request = new StrategyComparisonRequest();
      request.setSymbol("AAPL");
      request.setStartDate(LocalDate.of(2023, 1, 1));
      request.setEndDate(LocalDate.of(2024, 1, 1));
      request.setInvestmentAmount(BigDecimal.valueOf(12000));
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

      given(dcaStrategy.execute(any())).willReturn(dcaResponse);
      given(conditionalPurchaseStrategy.execute(any())).willReturn(conditionalResponse);
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
      verify(dcaStrategy).execute(any());
      verify(conditionalPurchaseStrategy).execute(any());
    }

    @Test
    @DisplayName("전략 타입이 없는 항목은 실패로 빼고 나머지로 비교한다")
    void compareStrategies_NoStrategyType_SkipsThatStrategy() {
      StrategyParameter dcaConfig = new StrategyParameter();
      dcaConfig.setStrategyType(StrategyType.DCA);
      dcaConfig.setName("DCA 전략");
      dcaConfig.setAmountPerPurchase(BigDecimal.valueOf(1000));
      dcaConfig.setPurchaseDay(1);
      StrategyParameter noType = new StrategyParameter();
      noType.setName("타입 없음");
      noType.setPurchaseDate(LocalDate.of(2023, 6, 1));
      StrategyComparisonRequest request = new StrategyComparisonRequest();
      request.setSymbol("AAPL");
      request.setStartDate(LocalDate.of(2023, 1, 1));
      request.setEndDate(LocalDate.of(2024, 1, 1));
      request.setInvestmentAmount(BigDecimal.valueOf(10000));
      request.setUserId("user123");
      request.setStrategies(List.of(dcaConfig, noType));

      StrategyResponse dcaResponse = StrategyResponse.builder().symbol("AAPL").totalReturnPercent(BigDecimal.valueOf(12)).build();
      ComparisonItem dcaItem = ComparisonItem.builder().name("DCA 전략").totalReturnPercent(BigDecimal.valueOf(12)).build();
      given(dcaStrategy.execute(any())).willReturn(dcaResponse);
      given(responseMapper.toComparisonItemFromStrategy(dcaResponse, "DCA 전략")).willReturn(dcaItem);
      given(responseMapper.toComparisonResponse(eq(request), any(), any()))
        .willReturn(ComparisonResponse.builder().bestPerformer(dcaItem).build());

      ComparisonResponse result = backtestAnalysisService.compareStrategies(request);

      assertThat(result.getBestPerformer()).isEqualTo(dcaItem);
      verify(responseMapper).toComparisonResponse(eq(request), argThat(items -> items.size() == 1 && items.get(0) == dcaItem), any());
      verify(tradingSimulationService, never()).runSimulation(any(), eq(false));
    }

    @Test
    @DisplayName("단순 매수 전략은 시뮬레이션으로 돈다")
    void compareStrategies_Simple_RunsSimulation() {
      StrategyParameter simple = new StrategyParameter();
      simple.setStrategyType(StrategyType.SIMPLE);
      simple.setName("단순 매수");
      simple.setPurchaseDate(LocalDate.of(2023, 6, 1));
      StrategyComparisonRequest request = new StrategyComparisonRequest();
      request.setSymbol("AAPL");
      request.setStartDate(LocalDate.of(2023, 1, 1));
      request.setEndDate(LocalDate.of(2024, 1, 1));
      request.setInvestmentAmount(BigDecimal.valueOf(10000));
      request.setUserId("user123");
      request.setStrategies(List.of(simple));

      SimulationResponse simulationResponse = SimulationResponse.builder().symbol("AAPL").totalReturnPercent(BigDecimal.valueOf(18)).build();
      ComparisonItem item = ComparisonItem.builder().name("단순 매수").totalReturnPercent(BigDecimal.valueOf(18)).build();
      given(tradingSimulationService.runSimulation(any(), eq(false))).willReturn(simulationResponse);
      given(responseMapper.toComparisonItemFromSimulation(simulationResponse, "단순 매수")).willReturn(item);
      given(responseMapper.toComparisonResponse(eq(request), any(), any()))
        .willReturn(ComparisonResponse.builder().bestPerformer(item).build());

      assertThat(backtestAnalysisService.compareStrategies(request).getBestPerformer()).isEqualTo(item);
      verify(tradingSimulationService).runSimulation(argThat(s -> LocalDate.of(2023, 6, 1).equals(s.getPurchaseDate())), eq(false));
    }
  }

  @Nested
  @DisplayName("비교 옵션 전달")
  class ComparisonOptionsTests {

    private final ComparisonItem item = ComparisonItem.builder()
      .name("항목")
      .totalReturnPercent(BigDecimal.TEN)
      .build();

    private StrategyParameter strategy(StrategyType type) {
      StrategyParameter parameter = new StrategyParameter();
      parameter.setStrategyType(type);
      parameter.setName(type.name());
      return parameter;
    }

    private StrategyComparisonRequest strategyRequest(StrategyParameter... strategies) {
      StrategyComparisonRequest request = new StrategyComparisonRequest();
      request.setSymbol("AAPL");
      request.setStartDate(LocalDate.of(2023, 1, 1));
      request.setEndDate(LocalDate.of(2024, 1, 1));
      request.setInvestmentAmount(BigDecimal.valueOf(1_000_000));
      request.setStrategies(Arrays.asList(strategies));
      request.setUserId("user123");
      return request;
    }

    // 수수료 0.25% · 수동 환율 1300/1350 · 재투자 · 배당세 15% · 양도세 · 기간 끝 매도
    private void withOptions(BaseComparisonRequest request) {
      request.setTradingFeeRate(new BigDecimal("0.0025"));
      request.setPurchaseFxRate(new BigDecimal("1300"));
      request.setCurrentFxRate(new BigDecimal("1350"));
      request.setReinvestDividends(true);
      request.setDividendTaxRate(new BigDecimal("0.15"));
      request.setCapitalGainsTax(true);
      request.setPeriodEnd(PeriodEnd.SELL_AT_END);
    }

    private void assertSimulationOptions(SimulationRequest simulation) {
      assertThat(simulation.getTradingFeeRate()).isEqualByComparingTo(new BigDecimal("0.0025"));
      assertThat(simulation.getPurchaseFxRate()).isEqualByComparingTo(new BigDecimal("1300"));
      assertThat(simulation.getCurrentFxRate()).isEqualByComparingTo(new BigDecimal("1350"));
      assertThat(simulation.getReinvestDividends()).isTrue();
      assertThat(simulation.getDividendTaxRate()).isEqualByComparingTo(new BigDecimal("0.15"));
      assertThat(simulation.getCapitalGainsTax()).isTrue();
      assertThat(simulation.getPeriodEnd()).isEqualTo(PeriodEnd.SELL_AT_END);
    }

    private void stubSimulation() {
      given(tradingSimulationService.runSimulation(any(), eq(false)))
        .willReturn(SimulationResponse.builder().build());
      given(responseMapper.toComparisonItemFromSimulation(any(), any())).willReturn(item);
    }

    private void stubComparisonResponse() {
      given(responseMapper.toComparisonResponse(any(), any(), any()))
        .willReturn(ComparisonResponse.builder().build());
    }

    @Test
    @DisplayName("전략 비교는 단순 · 적립식 · 조건부 하위 요청에 공통 옵션을, 적립식에는 칩의 회당 금액 · 주기 · 첫 매수일 · 투자일과 원금 한도를, 조건부에는 칩의 회당 금액과 원금 예산을 넘기고 익절 · 손절이 있는 조건부는 재투자를 끈다")
    void compareStrategies_PassesOptions() {
      StrategyParameter simple = strategy(StrategyType.SIMPLE);
      simple.setPurchaseDate(LocalDate.of(2023, 1, 1));
      StrategyParameter dca = strategy(StrategyType.DCA);
      dca.setAmountPerPurchase(BigDecimal.valueOf(100_000));
      dca.setFrequency(DcaFrequency.MONTHLY);
      dca.setFirstPurchaseDate(LocalDate.of(2023, 1, 31));
      dca.setPurchaseDay(31);
      dca.setInvestmentInterval(2);
      StrategyParameter conditional = strategy(StrategyType.CONDITIONAL_PURCHASE);
      conditional.setAmountPerPurchase(BigDecimal.valueOf(200_000));
      conditional.setDropPercentage(new BigDecimal("0.05"));
      conditional.setDirection(PriceDirection.RISE);
      conditional.setTakeProfitRate(new BigDecimal("0.2"));
      conditional.setStopLossRate(new BigDecimal("0.1"));
      StrategyComparisonRequest request = strategyRequest(simple, dca, conditional);
      withOptions(request);

      stubSimulation();
      given(dcaStrategy.execute(any())).willReturn(StrategyResponse.builder().build());
      given(conditionalPurchaseStrategy.execute(any()))
        .willReturn(StrategyResponse.builder().build());
      given(responseMapper.toComparisonItemFromStrategy(any(), any())).willReturn(item);
      stubComparisonResponse();

      backtestAnalysisService.compareStrategies(request);

      ArgumentCaptor<SimulationRequest> simulation = ArgumentCaptor.forClass(SimulationRequest.class);
      verify(tradingSimulationService).runSimulation(simulation.capture(), eq(false));
      assertThat(simulation.getValue().getPurchaseDate()).isEqualTo(LocalDate.of(2023, 1, 1));
      assertThat(simulation.getValue().getSaleDate()).isEqualTo(LocalDate.of(2024, 1, 1));
      assertSimulationOptions(simulation.getValue());

      ArgumentCaptor<DcaStrategyRequest> dcaRequest = ArgumentCaptor.forClass(DcaStrategyRequest.class);
      verify(dcaStrategy).execute(dcaRequest.capture());
      assertThat(dcaRequest.getValue().getAmountPerPurchase()).isEqualByComparingTo("100000");
      assertThat(dcaRequest.getValue().getSharesPerPurchase()).isNull();
      assertThat(dcaRequest.getValue().getFrequency()).isEqualTo(DcaFrequency.MONTHLY);
      assertThat(dcaRequest.getValue().getFirstPurchaseDate()).isEqualTo(LocalDate.of(2023, 1, 31));
      assertThat(dcaRequest.getValue().getPurchaseDay()).isEqualTo(31);
      assertThat(dcaRequest.getValue().getInvestmentInterval()).isEqualTo(2);
      assertThat(dcaRequest.getValue().getTotalInvestmentLimit())
        .isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
      assertThat(dcaRequest.getValue().getPurchaseFxRate()).isEqualByComparingTo(new BigDecimal("1300"));
      assertThat(dcaRequest.getValue().getCurrentFxRate()).isEqualByComparingTo(new BigDecimal("1350"));
      assertThat(dcaRequest.getValue().getReinvestDividends()).isTrue();
      assertThat(dcaRequest.getValue().getDividendTaxRate()).isEqualByComparingTo(new BigDecimal("0.15"));
      assertThat(dcaRequest.getValue().getTradingFeeRate()).isEqualByComparingTo(new BigDecimal("0.0025"));
      assertThat(dcaRequest.getValue().getCapitalGainsTax()).isTrue();
      assertThat(dcaRequest.getValue().getPeriodEnd()).isEqualTo(PeriodEnd.SELL_AT_END);

      ArgumentCaptor<ConditionalStrategyRequest> conditionalRequest =
        ArgumentCaptor.forClass(ConditionalStrategyRequest.class);
      verify(conditionalPurchaseStrategy).execute(conditionalRequest.capture());
      assertThat(conditionalRequest.getValue().getPurchaseFxRate())
        .isEqualByComparingTo(new BigDecimal("1300"));
      assertThat(conditionalRequest.getValue().getCurrentFxRate())
        .isEqualByComparingTo(new BigDecimal("1350"));
      assertThat(conditionalRequest.getValue().getReinvestDividends()).isFalse();
      assertThat(conditionalRequest.getValue().getDividendTaxRate())
        .isEqualByComparingTo(new BigDecimal("0.15"));
      assertThat(conditionalRequest.getValue().getTradingFeeRate())
        .isEqualByComparingTo(new BigDecimal("0.0025"));
      assertThat(conditionalRequest.getValue().getDirection()).isEqualTo(PriceDirection.RISE);
      assertThat(conditionalRequest.getValue().getTakeProfitRate()).isEqualByComparingTo("0.2");
      assertThat(conditionalRequest.getValue().getStopLossRate()).isEqualByComparingTo("0.1");
      assertThat(conditionalRequest.getValue().getAmountPerPurchase()).isEqualByComparingTo("200000");
      assertThat(conditionalRequest.getValue().getSharesPerPurchase()).isNull();
      assertThat(conditionalRequest.getValue().getMaxPurchases()).isNull();
      assertThat(conditionalRequest.getValue().getTotalInvestment())
        .isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
      assertThat(conditionalRequest.getValue().getCapitalGainsTax()).isTrue();
      assertThat(conditionalRequest.getValue().getPeriodEnd()).isEqualTo(PeriodEnd.SELL_AT_END);
    }

    @Test
    @DisplayName("전략 비교의 조건부는 칩의 회당 금액 · 최대 횟수를 그대로 넘기고 원금을 예산으로 쓴다")
    void compareStrategies_ConditionalKeepsChipLimits() {
      StrategyParameter conditional = strategy(StrategyType.CONDITIONAL_PURCHASE);
      conditional.setAmountPerPurchase(BigDecimal.valueOf(300_000));
      conditional.setDropPercentage(new BigDecimal("0.05"));
      conditional.setMaxPurchases(4);
      given(conditionalPurchaseStrategy.execute(any())).willReturn(StrategyResponse.builder().build());
      given(responseMapper.toComparisonItemFromStrategy(any(), any())).willReturn(item);
      stubComparisonResponse();

      backtestAnalysisService.compareStrategies(strategyRequest(conditional));

      ArgumentCaptor<ConditionalStrategyRequest> conditionalRequest =
        ArgumentCaptor.forClass(ConditionalStrategyRequest.class);
      verify(conditionalPurchaseStrategy).execute(conditionalRequest.capture());
      assertThat(conditionalRequest.getValue().getAmountPerPurchase()).isEqualByComparingTo("300000");
      assertThat(conditionalRequest.getValue().getMaxPurchases()).isEqualTo(4);
      assertThat(conditionalRequest.getValue().getTotalInvestment())
        .isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
    }

    @Test
    @DisplayName("익절 · 손절이 없는 조건부 칩은 고른 배당 재투자를 그대로 넘긴다")
    void compareStrategies_ConditionalWithoutSellsKeepsReinvest() {
      StrategyParameter conditional = strategy(StrategyType.CONDITIONAL_PURCHASE);
      conditional.setAmountPerPurchase(BigDecimal.valueOf(300_000));
      conditional.setDropPercentage(new BigDecimal("0.05"));
      StrategyComparisonRequest request = strategyRequest(conditional);
      request.setReinvestDividends(true);
      given(conditionalPurchaseStrategy.execute(any())).willReturn(StrategyResponse.builder().build());
      given(responseMapper.toComparisonItemFromStrategy(any(), any())).willReturn(item);
      stubComparisonResponse();

      backtestAnalysisService.compareStrategies(request);

      verify(conditionalPurchaseStrategy).execute(argThat(r ->
        Boolean.TRUE.equals(((ConditionalStrategyRequest) r).getReinvestDividends())));
    }

    @Test
    @DisplayName("전략 비교의 이동평균 칩은 비교 원금 · 칩의 짧은 · 긴 기간과 공통 옵션을 넘기고 배당 재투자는 끈다")
    void compareStrategies_MaCrossPassesWindows() {
      StrategyParameter maCross = strategy(StrategyType.MA_CROSS);
      maCross.setShortWindow(5);
      maCross.setLongWindow(20);
      StrategyComparisonRequest request = strategyRequest(maCross);
      withOptions(request);
      given(maCrossStrategy.execute(any())).willReturn(StrategyResponse.builder().build());
      given(responseMapper.toComparisonItemFromStrategy(any(), eq("MA_CROSS"))).willReturn(item);
      stubComparisonResponse();

      backtestAnalysisService.compareStrategies(request);

      ArgumentCaptor<MaCrossStrategyRequest> maCrossRequest = ArgumentCaptor.forClass(MaCrossStrategyRequest.class);
      verify(maCrossStrategy).execute(maCrossRequest.capture());
      MaCrossStrategyRequest sent = maCrossRequest.getValue();
      assertThat(sent.getSymbol()).isEqualTo("AAPL");
      assertThat(sent.getStartDate()).isEqualTo(LocalDate.of(2023, 1, 1));
      assertThat(sent.getEndDate()).isEqualTo(LocalDate.of(2024, 1, 1));
      assertThat(sent.getUserId()).isEqualTo("user123");
      assertThat(sent.getInvestmentAmount()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
      assertThat(sent.getShortWindow()).isEqualTo(5);
      assertThat(sent.getLongWindow()).isEqualTo(20);
      assertThat(sent.getReinvestDividends()).isFalse();
      assertThat(sent.getPurchaseFxRate()).isEqualByComparingTo(new BigDecimal("1300"));
      assertThat(sent.getCurrentFxRate()).isEqualByComparingTo(new BigDecimal("1350"));
      assertThat(sent.getDividendTaxRate()).isEqualByComparingTo(new BigDecimal("0.15"));
      assertThat(sent.getTradingFeeRate()).isEqualByComparingTo(new BigDecimal("0.0025"));
      assertThat(sent.getCapitalGainsTax()).isTrue();
      assertThat(sent.getPeriodEnd()).isEqualTo(PeriodEnd.SELL_AT_END);
    }

    @Test
    @DisplayName("전략 비교의 이동평균 칩 옵션이 비면 수수료 0 · 배당세 0 으로 넘기고 비운 기간은 그대로 둔다")
    void compareStrategies_MaCrossEmptyOptions() {
      StrategyComparisonRequest request = strategyRequest(strategy(StrategyType.MA_CROSS));
      given(maCrossStrategy.execute(any())).willReturn(StrategyResponse.builder().build());
      given(responseMapper.toComparisonItemFromStrategy(any(), any())).willReturn(item);
      stubComparisonResponse();

      backtestAnalysisService.compareStrategies(request);

      verify(maCrossStrategy).execute(argThat(r -> {
        MaCrossStrategyRequest sent = (MaCrossStrategyRequest) r;
        return sent.getTradingFeeRate().signum() == 0 && sent.getDividendTaxRate().signum() == 0
          && sent.getShortWindow() == null && sent.getLongWindow() == null;
      }));
    }

    @Test
    @DisplayName("전략 비교의 밴드 칩은 비교 원금 · 칩의 평균 기간 · 위 · 아래 비율과 공통 옵션을 넘기고 배당 재투자는 끈다")
    void compareStrategies_MaBandPassesBand() {
      StrategyParameter maBand = strategy(StrategyType.MA_BAND);
      maBand.setBandWindow(100);
      maBand.setUpperRate(new BigDecimal("0.03"));
      maBand.setLowerRate(new BigDecimal("0.05"));
      StrategyComparisonRequest request = strategyRequest(maBand);
      withOptions(request);
      given(maBandStrategy.execute(any())).willReturn(StrategyResponse.builder().build());
      given(responseMapper.toComparisonItemFromStrategy(any(), eq("MA_BAND"))).willReturn(item);
      stubComparisonResponse();

      backtestAnalysisService.compareStrategies(request);

      ArgumentCaptor<MaBandStrategyRequest> maBandRequest = ArgumentCaptor.forClass(MaBandStrategyRequest.class);
      verify(maBandStrategy).execute(maBandRequest.capture());
      MaBandStrategyRequest sent = maBandRequest.getValue();
      assertThat(sent.getSymbol()).isEqualTo("AAPL");
      assertThat(sent.getStartDate()).isEqualTo(LocalDate.of(2023, 1, 1));
      assertThat(sent.getEndDate()).isEqualTo(LocalDate.of(2024, 1, 1));
      assertThat(sent.getUserId()).isEqualTo("user123");
      assertThat(sent.getInvestmentAmount()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
      assertThat(sent.getBandWindow()).isEqualTo(100);
      assertThat(sent.getUpperRate()).isEqualByComparingTo("0.03");
      assertThat(sent.getLowerRate()).isEqualByComparingTo("0.05");
      assertThat(sent.getReinvestDividends()).isFalse();
      assertThat(sent.getPurchaseFxRate()).isEqualByComparingTo(new BigDecimal("1300"));
      assertThat(sent.getCurrentFxRate()).isEqualByComparingTo(new BigDecimal("1350"));
      assertThat(sent.getDividendTaxRate()).isEqualByComparingTo(new BigDecimal("0.15"));
      assertThat(sent.getTradingFeeRate()).isEqualByComparingTo(new BigDecimal("0.0025"));
      assertThat(sent.getCapitalGainsTax()).isTrue();
      assertThat(sent.getPeriodEnd()).isEqualTo(PeriodEnd.SELL_AT_END);
    }

    @Test
    @DisplayName("전략 비교의 밴드 칩 옵션이 비면 수수료 0 · 배당세 0 으로 넘기고 비운 밴드 칸은 그대로 둔다")
    void compareStrategies_MaBandEmptyOptions() {
      StrategyComparisonRequest request = strategyRequest(strategy(StrategyType.MA_BAND));
      given(maBandStrategy.execute(any())).willReturn(StrategyResponse.builder().build());
      given(responseMapper.toComparisonItemFromStrategy(any(), any())).willReturn(item);
      stubComparisonResponse();

      backtestAnalysisService.compareStrategies(request);

      verify(maBandStrategy).execute(argThat(r -> {
        MaBandStrategyRequest sent = (MaBandStrategyRequest) r;
        return sent.getTradingFeeRate().signum() == 0 && sent.getDividendTaxRate().signum() == 0
          && sent.getBandWindow() == null && sent.getUpperRate() == null && sent.getLowerRate() == null;
      }));
    }

    @Test
    @DisplayName("옵션이 비면 재투자 끔 · 수수료 0 · 배당세 0 · 투자 주기 1 로, 적립식 한도는 원금으로 넘긴다")
    void compareStrategies_EmptyOptions_UsesDefaults() {
      StrategyParameter simple = strategy(StrategyType.SIMPLE);
      simple.setPurchaseDate(LocalDate.of(2023, 1, 1));
      StrategyParameter dca = strategy(StrategyType.DCA);
      dca.setAmountPerPurchase(BigDecimal.valueOf(100_000));
      dca.setPurchaseDay(15);
      StrategyComparisonRequest request = strategyRequest(simple, dca);

      stubSimulation();
      given(dcaStrategy.execute(any())).willReturn(StrategyResponse.builder().build());
      given(responseMapper.toComparisonItemFromStrategy(any(), any())).willReturn(item);
      stubComparisonResponse();

      backtestAnalysisService.compareStrategies(request);

      ArgumentCaptor<SimulationRequest> simulation = ArgumentCaptor.forClass(SimulationRequest.class);
      verify(tradingSimulationService).runSimulation(simulation.capture(), eq(false));
      assertThat(simulation.getValue().getTradingFeeRate()).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(simulation.getValue().getReinvestDividends()).isFalse();
      assertThat(simulation.getValue().getDividendTaxRate()).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(simulation.getValue().getPurchaseFxRate()).isNull();
      assertThat(simulation.getValue().getCapitalGainsTax()).isNull();
      assertThat(simulation.getValue().getPeriodEnd()).isNull();

      ArgumentCaptor<DcaStrategyRequest> dcaRequest = ArgumentCaptor.forClass(DcaStrategyRequest.class);
      verify(dcaStrategy).execute(dcaRequest.capture());
      assertThat(dcaRequest.getValue().getInvestmentInterval()).isEqualTo(1);
      assertThat(dcaRequest.getValue().getTotalInvestmentLimit())
        .isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
      assertThat(dcaRequest.getValue().getReinvestDividends()).isFalse();
      assertThat(dcaRequest.getValue().getDividendTaxRate()).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(dcaRequest.getValue().getTradingFeeRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("종목 비교는 종목마다 시뮬레이션 요청에 공통 옵션을 넘긴다")
    void compareSymbols_PassesOptions() {
      SymbolComparisonRequest request = new SymbolComparisonRequest();
      request.setSymbols(Arrays.asList("AAPL", "MSFT"));
      request.setStartDate(LocalDate.of(2023, 1, 1));
      request.setEndDate(LocalDate.of(2024, 1, 1));
      request.setInvestmentAmount(BigDecimal.valueOf(1_000_000));
      request.setUserId("user123");
      withOptions(request);

      stubSimulation();
      stubComparisonResponse();

      backtestAnalysisService.compareSymbols(request);

      ArgumentCaptor<SimulationRequest> simulations = ArgumentCaptor.forClass(SimulationRequest.class);
      verify(tradingSimulationService, times(2)).runSimulation(simulations.capture(), eq(false));
      assertThat(simulations.getAllValues())
        .extracting(SimulationRequest::getSymbol)
        .containsExactly("AAPL", "MSFT");
      assertThat(simulations.getAllValues())
        .extracting(SimulationRequest::getSaleDate)
        .containsOnly(LocalDate.of(2024, 1, 1));
      assertThat(simulations.getAllValues()).allSatisfy(this::assertSimulationOptions);
    }
  }

  @Nested
  @DisplayName("실패 경로")
  class FailurePathTests {

    private ConditionalStrategyRequest conditionalRequest() {
      return ConditionalStrategyRequest.builder().symbol("AAPL").userId("user123")
        .startDate(LocalDate.of(2023, 1, 1)).endDate(LocalDate.of(2024, 1, 1))
        .totalInvestment(BigDecimal.valueOf(12000)).dropPercentage(BigDecimal.valueOf(0.1)).build();
    }

    @Test
    @DisplayName("조건부 매수 전략이 실패하면 같은 예외를 올리고 이력은 남기지 않는다")
    void conditionalFailureRethrows() {
      BacktestException failure = new BacktestException(BacktestResponse.DATA_NOT_FOUND, "no data");
      given(conditionalPurchaseStrategy.execute(any())).willThrow(failure);

      assertThatThrownBy(() -> backtestAnalysisService.runConditionalStrategy(conditionalRequest())).isSameAs(failure);

      verify(backtestHistoryUtils, never()).saveBacktestHistory(any(), any(), any());
    }

    @Test
    @DisplayName("종목 비교에 지수가 있으면 어느 종목도 돌리지 않고 INDEX_NOT_TRADABLE 이고 이력은 남기지 않는다")
    void symbolComparisonRejectsIndex() {
      SymbolComparisonRequest request = new SymbolComparisonRequest();
      request.setSymbols(List.of("AAPL", "^GSPC"));
      request.setStartDate(LocalDate.of(2023, 1, 1));
      request.setInvestmentAmount(BigDecimal.valueOf(10000));
      request.setUserId("user123");

      assertThatThrownBy(() -> backtestAnalysisService.compareSymbols(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.INDEX_NOT_TRADABLE);
      verify(tradingSimulationService, never()).runSimulation(any(), eq(false));
      verify(backtestHistoryUtils, never()).saveBacktestHistory(any(), any(), any());
    }

    @Test
    @DisplayName("전략 비교의 종목이 지수면 어느 전략도 돌리지 않고 INDEX_NOT_TRADABLE 이고 이력은 남기지 않는다")
    void strategyComparisonRejectsIndex() {
      StrategyParameter maCross = new StrategyParameter();
      maCross.setStrategyType(StrategyType.MA_CROSS);
      StrategyComparisonRequest request = new StrategyComparisonRequest();
      request.setSymbol("^NDX");
      request.setStartDate(LocalDate.of(2023, 1, 1));
      request.setEndDate(LocalDate.of(2024, 1, 1));
      request.setInvestmentAmount(BigDecimal.valueOf(12000));
      request.setUserId("user123");
      request.setStrategies(List.of(maCross));

      assertThatThrownBy(() -> backtestAnalysisService.compareStrategies(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.INDEX_NOT_TRADABLE);
      verify(maCrossStrategy, never()).execute(any());
      verify(backtestHistoryUtils, never()).saveBacktestHistory(any(), any(), any());
    }

    @Test
    @DisplayName("등록된 전략이 없으면 STRATEGY_TYPE_MISMATCH")
    void missingStrategy() {
      BacktestAnalysisServiceImpl dcaOnly = new BacktestAnalysisServiceImpl(tradingSimulationService,
        List.of(dcaStrategy), responseMapper, backtestHistoryUtils, portfolioEngine);

      assertThatThrownBy(() -> dcaOnly.runConditionalStrategy(conditionalRequest()))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.STRATEGY_TYPE_MISMATCH);
    }

    @Test
    @DisplayName("전략 비교에서 실패한 전략은 빼고 나머지로 비교하고, 사용자가 없으면 이력을 남기지 않는다")
    void strategyComparisonSkipsFailed() {
      StrategyParameter dca = new StrategyParameter();
      dca.setStrategyType(StrategyType.DCA);
      dca.setAmountPerPurchase(BigDecimal.valueOf(1000));
      dca.setPurchaseDay(1);
      StrategyParameter conditional = new StrategyParameter();
      conditional.setStrategyType(StrategyType.CONDITIONAL_PURCHASE);
      conditional.setName("조건부 매수");
      conditional.setAmountPerPurchase(BigDecimal.valueOf(2000));
      conditional.setDropPercentage(BigDecimal.valueOf(0.1));

      StrategyComparisonRequest request = new StrategyComparisonRequest();
      request.setSymbol("AAPL");
      request.setStartDate(LocalDate.of(2023, 1, 1));
      request.setEndDate(LocalDate.of(2024, 1, 1));
      request.setStrategies(List.of(dca, conditional));

      StrategyResponse conditionalResult = StrategyResponse.builder().totalReturnPercent(BigDecimal.TEN).build();
      ComparisonItem conditionalItem = ComparisonItem.builder().name("조건부 매수").totalReturnPercent(BigDecimal.TEN).build();
      given(dcaStrategy.execute(any())).willThrow(new IllegalStateException("dca down"));
      given(conditionalPurchaseStrategy.execute(any())).willReturn(conditionalResult);
      given(responseMapper.toComparisonItemFromStrategy(conditionalResult, "조건부 매수")).willReturn(conditionalItem);
      given(responseMapper.toComparisonResponse(eq(request), any(), any())).willReturn(ComparisonResponse.builder().build());

      backtestAnalysisService.compareStrategies(request);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<ComparisonItem>> items = ArgumentCaptor.forClass(List.class);
      verify(responseMapper).toComparisonResponse(eq(request), items.capture(), any());
      assertThat(items.getValue()).containsExactly(conditionalItem);
      verify(backtestHistoryUtils, never()).saveBacktestHistory(any(), any(), any());
    }

    @ParameterizedTest(name = "첫 매수일 {0}")
    @ValueSource(strings = {"2022-12-31", "2024-01-02"})
    @DisplayName("전략 비교에 첫 매수일이 기간 밖인 적립식 칩이 있으면 어느 전략도 돌리지 않고 INVALID_FIRST_PURCHASE_DATE 이고 이력은 남기지 않는다")
    void strategyComparisonRejectsFirstPurchaseOutOfPeriod(String first) {
      StrategyParameter conditional = new StrategyParameter();
      conditional.setStrategyType(StrategyType.CONDITIONAL_PURCHASE);
      conditional.setAmountPerPurchase(BigDecimal.valueOf(2000));
      conditional.setDropPercentage(BigDecimal.valueOf(0.1));
      StrategyParameter dca = new StrategyParameter();
      dca.setStrategyType(StrategyType.DCA);
      dca.setAmountPerPurchase(BigDecimal.valueOf(1000));
      dca.setFirstPurchaseDate(LocalDate.parse(first));

      StrategyComparisonRequest request = new StrategyComparisonRequest();
      request.setSymbol("AAPL");
      request.setStartDate(LocalDate.of(2023, 1, 1));
      request.setEndDate(LocalDate.of(2024, 1, 1));
      request.setInvestmentAmount(BigDecimal.valueOf(12000));
      request.setUserId("user123");
      request.setStrategies(List.of(conditional, dca));

      assertThatThrownBy(() -> backtestAnalysisService.compareStrategies(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.INVALID_FIRST_PURCHASE_DATE);
      verify(conditionalPurchaseStrategy, never()).execute(any());
      verify(dcaStrategy, never()).execute(any());
      verify(backtestHistoryUtils, never()).saveBacktestHistory(any(), any(), any());
    }

    @Test
    @DisplayName("적립식 칩의 첫 매수일이 시작일 · 종료일과 같으면 기간 안으로 보고 돌린다")
    void strategyComparisonAcceptsFirstPurchaseOnPeriodEdges() {
      StrategyParameter onStart = new StrategyParameter();
      onStart.setStrategyType(StrategyType.DCA);
      onStart.setAmountPerPurchase(BigDecimal.valueOf(1000));
      onStart.setFirstPurchaseDate(LocalDate.of(2023, 1, 1));
      StrategyParameter onEnd = new StrategyParameter();
      onEnd.setStrategyType(StrategyType.DCA);
      onEnd.setAmountPerPurchase(BigDecimal.valueOf(1000));
      onEnd.setFirstPurchaseDate(LocalDate.of(2024, 1, 1));

      StrategyComparisonRequest request = new StrategyComparisonRequest();
      request.setSymbol("AAPL");
      request.setStartDate(LocalDate.of(2023, 1, 1));
      request.setEndDate(LocalDate.of(2024, 1, 1));
      request.setInvestmentAmount(BigDecimal.valueOf(12000));
      request.setStrategies(List.of(onStart, onEnd));

      StrategyResponse result = StrategyResponse.builder().totalReturnPercent(BigDecimal.TEN).build();
      given(dcaStrategy.execute(any())).willReturn(result);
      given(responseMapper.toComparisonItemFromStrategy(eq(result), any()))
        .willReturn(ComparisonItem.builder().totalReturnPercent(BigDecimal.TEN).build());
      given(responseMapper.toComparisonResponse(eq(request), any(), any())).willReturn(ComparisonResponse.builder().build());

      backtestAnalysisService.compareStrategies(request);

      verify(dcaStrategy, times(2)).execute(any());
    }
  }


  @Nested
  @DisplayName("연말 정리 비교")
  class YearEndComparisonTests {

    private final LocalDate start = LocalDate.of(2021, 1, 1);

    // 양도세 합만 있는 결과
    private StrategyResponse taxed(String totalTaxKrw) {
      return StrategyResponse.builder().capitalGainsTax(new CapitalGainsTax(List.of(), new BigDecimal(totalTaxKrw),
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)).build();
    }

    // 연말 정리를 끄고 기간 끝에 팔면 400만원, 켜고 기간 끝에 팔면 240만원, 켜고 끝까지 보유면 10만원
    private StrategyResponse taxedBy(Boolean yearEndHarvest, PeriodEnd periodEnd) {
      if (!Boolean.TRUE.equals(yearEndHarvest)) {
        return taxed("4000000");
      }
      return taxed(periodEnd == PeriodEnd.SELL_AT_END ? "2400000" : "100000");
    }

    private DcaStrategyRequest.DcaStrategyRequestBuilder dca(LocalDate end) {
      return DcaStrategyRequest.builder().userId("user123").symbol("SPY").startDate(start).endDate(end)
        .amountPerPurchase(new BigDecimal("1000000")).capitalGainsTax(true).yearEndHarvest(true);
    }

    private void stubDca() {
      given(dcaStrategy.execute(any())).willAnswer(invocation -> {
        DcaStrategyRequest request = invocation.getArgument(0);
        return taxedBy(request.getYearEndHarvest(), request.getPeriodEnd());
      });
    }

    private List<Tuple> dcaRuns(int times) {
      ArgumentCaptor<DcaStrategyRequest> runs = ArgumentCaptor.forClass(DcaStrategyRequest.class);
      verify(dcaStrategy, times(times)).execute(runs.capture());
      return runs.getAllValues().stream()
        .map(run -> org.assertj.core.api.Assertions.tuple(run.getYearEndHarvest(), run.getPeriodEnd(), run.getUserId()))
        .toList();
    }

    @Test
    @DisplayName("끝까지 보유 요청은 연말 정리를 끄고 · 켜고 기간 끝에 판 사본을 더 돌려 양도세 차이를 싣고 이력은 원래 요청 한 번만 남긴다")
    void holdRunsTwiceMore() {
      stubDca();
      DcaStrategyRequest request = dca(LocalDate.of(2023, 12, 31)).build();

      StrategyResponse response = backtestAnalysisService.runDcaStrategy(request);

      assertThat(response.getCapitalGainsTax().totalTaxKrw()).isEqualByComparingTo("100000");
      assertThat(response.getCapitalGainsTax().harvestComparison()).isEqualTo(new CapitalGainsTax.HarvestComparison(
        new BigDecimal("4000000"), new BigDecimal("2400000"), new BigDecimal("1600000")));
      assertThat(dcaRuns(3)).containsExactly(
        org.assertj.core.api.Assertions.tuple(true, null, "user123"),
        org.assertj.core.api.Assertions.tuple(false, PeriodEnd.SELL_AT_END, null),
        org.assertj.core.api.Assertions.tuple(true, PeriodEnd.SELL_AT_END, null));
      verify(backtestHistoryUtils).saveBacktestHistory("user123", BacktestType.STRATEGY_SIMULATION, request);
    }

    @Test
    @DisplayName("기간 끝 매도 요청은 연말 정리를 끈 사본만 더 돌리고 자기 양도세를 연말 정리 쪽으로 쓴다")
    void sellAtEndRunsOnceMore() {
      stubDca();

      StrategyResponse response = backtestAnalysisService.runDcaStrategy(
        dca(LocalDate.of(2023, 12, 31)).periodEnd(PeriodEnd.SELL_AT_END).build());

      assertThat(response.getCapitalGainsTax().harvestComparison()).isEqualTo(new CapitalGainsTax.HarvestComparison(
        new BigDecimal("4000000"), new BigDecimal("2400000"), new BigDecimal("1600000")));
      assertThat(dcaRuns(2)).containsExactly(
        org.assertj.core.api.Assertions.tuple(true, PeriodEnd.SELL_AT_END, "user123"),
        org.assertj.core.api.Assertions.tuple(false, PeriodEnd.SELL_AT_END, null));
    }

    @Test
    @DisplayName("종료일이 오늘 뒤면 오늘까지로 2년을 센다")
    void futureEndUsesToday() {
      stubDca();

      StrategyResponse response = backtestAnalysisService.runDcaStrategy(dca(LocalDate.now().plusDays(10)).build());

      assertThat(response.getCapitalGainsTax().harvestComparison()).isNotNull();
      verify(dcaStrategy, times(3)).execute(any());
    }

    @Test
    @DisplayName("기간이 2년이 안 되거나 연말 정리 · 양도세를 끄면 비교 없이 한 번만 돌린다")
    void noComparison() {
      stubDca();

      StrategyResponse shortPeriod = backtestAnalysisService.runDcaStrategy(dca(LocalDate.of(2022, 12, 31)).build());
      assertThat(shortPeriod.getCapitalGainsTax().harvestComparison()).isNull();
      backtestAnalysisService.runDcaStrategy(dca(LocalDate.of(2023, 12, 31)).yearEndHarvest(false).build());
      backtestAnalysisService.runDcaStrategy(dca(LocalDate.of(2023, 12, 31)).capitalGainsTax(false).build());

      verify(dcaStrategy, times(3)).execute(any());
    }

    @Test
    @DisplayName("조건부 · 이동평균 · 밴드 요청도 연말 정리를 끄고 기간 끝에 파는 사본으로 비교한다")
    void otherStrategiesCompare() {
      given(conditionalPurchaseStrategy.execute(any())).willAnswer(invocation -> {
        ConditionalStrategyRequest request = invocation.getArgument(0);
        return taxedBy(request.getYearEndHarvest(), request.getPeriodEnd());
      });
      given(maCrossStrategy.execute(any())).willAnswer(invocation -> {
        MaCrossStrategyRequest request = invocation.getArgument(0);
        return taxedBy(request.getYearEndHarvest(), request.getPeriodEnd());
      });
      given(maBandStrategy.execute(any())).willAnswer(invocation -> {
        MaBandStrategyRequest request = invocation.getArgument(0);
        return taxedBy(request.getYearEndHarvest(), request.getPeriodEnd());
      });
      LocalDate end = LocalDate.of(2023, 12, 31);

      List<StrategyResponse> responses = List.of(
        backtestAnalysisService.runConditionalStrategy(ConditionalStrategyRequest.builder().symbol("SPY")
          .startDate(start).endDate(end).amountPerPurchase(BigDecimal.TEN).dropPercentage(new BigDecimal("0.1"))
          .capitalGainsTax(true).yearEndHarvest(true).periodEnd(PeriodEnd.SELL_AT_END).build()),
        backtestAnalysisService.runMaCrossStrategy(MaCrossStrategyRequest.builder().symbol("SPY").startDate(start)
          .endDate(end).investmentAmount(BigDecimal.TEN).capitalGainsTax(true).yearEndHarvest(true)
          .periodEnd(PeriodEnd.SELL_AT_END).build()),
        backtestAnalysisService.runMaBandStrategy(MaBandStrategyRequest.builder().symbol("SPY").startDate(start)
          .endDate(end).investmentAmount(BigDecimal.TEN).capitalGainsTax(true).yearEndHarvest(true)
          .periodEnd(PeriodEnd.SELL_AT_END).build()));

      assertThat(responses).extracting(response -> response.getCapitalGainsTax().harvestComparison().savedKrw())
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("1600000"), new BigDecimal("1600000"), new BigDecimal("1600000"));
    }

    // 2021년 1월 4일 SPY 1만 달러어치를 산 거래와 판 거래(판 날 · 판 돈 USD · 이유)
    private PortfolioEngine.Result engineResult(List<String[]> sales) {
      List<StrategyTransaction> transactions = new java.util.ArrayList<>();
      transactions.add(StrategyTransaction.builder().symbol("SPY").reason(TradeReason.BUY)
        .date(LocalDate.of(2021, 1, 4)).actualDate(LocalDate.of(2021, 1, 4)).price(BigDecimal.TEN)
        .shares(new BigDecimal("1000")).amount(new BigDecimal("10000000")).fxRate(new BigDecimal("1000")).build());
      for (String[] sale : sales) {
        LocalDate date = LocalDate.parse(sale[0]);
        transactions.add(StrategyTransaction.builder().symbol("SPY").reason(TradeReason.PERIOD_END_SALE).date(date)
          .actualDate(date).price(BigDecimal.TEN).shares(new BigDecimal(sale[2]).negate()).amount(BigDecimal.ZERO)
          .proceeds(new BigDecimal(sale[1])).realizedProfit(BigDecimal.ZERO).fxRate(new BigDecimal("1000"))
          .trigger(sale[3]).build());
      }
      return new PortfolioEngine.Result(LocalDate.of(2021, 1, 4), LocalDate.of(2023, 12, 29), transactions, List.of(),
        List.of(new PortfolioEngine.Position("SPY", PortfolioStrategyType.LUMP_SUM, new BigDecimal("10000000"),
          BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO)), new BigDecimal("10000000"), BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000"),
        new StrategyPerformance.Result(List.of(), null, null), CapitalGainsTaxCalculator.Payments.NONE, List.of());
    }

    @Test
    @DisplayName("포트폴리오도 끝까지 보유 요청이면 엔진을 두 번 더 돌려 기간 끝에 한 번에 판 양도세와 견준다")
    void portfolioCompares() {
      PortfolioRequest request = PortfolioRequest.builder().userId("user123").startDate(start)
        .endDate(LocalDate.of(2023, 12, 31))
        .assets(List.of(new PortfolioAsset("SPY", new BigDecimal("10000000"), null, null, null, null, null)))
        .capitalGainsTax(true).yearEndHarvest(true).build();
      given(portfolioEngine.run(any())).willAnswer(invocation -> {
        PortfolioRequest run = invocation.getArgument(0);
        if (!Boolean.TRUE.equals(run.getYearEndHarvest())) {
          // 1000만원어치를 2000만원에 한 번에 팔아 1000만원 이익
          return engineResult(List.<String[]>of(new String[] {"2023-12-29", "20000", "1000", "기간 끝 매도"}));
        }
        if (run.getPeriodEnd() == PeriodEnd.SELL_AT_END) {
          // 2021 · 2022 연말에 250만원씩, 끝에 나머지 500만원
          return engineResult(List.<String[]>of(new String[] {"2021-12-31", "5000", "250", "공제 채우기 매도"},
            new String[] {"2022-12-30", "5000", "250", "공제 채우기 매도"},
            new String[] {"2023-12-29", "10000", "500", "기간 끝 매도"}));
        }
        return engineResult(List.of());
      });

      PortfolioResponse response = backtestAnalysisService.runPortfolio(request);
      PortfolioResponse sellAtEnd = backtestAnalysisService.runPortfolio(
        request.toBuilder().periodEnd(PeriodEnd.SELL_AT_END).build());

      // 750만원 × 22% · 250만원 × 22%, 기간 끝 매도 요청은 자기 양도세를 연말 정리 쪽으로
      CapitalGainsTax.HarvestComparison expected = new CapitalGainsTax.HarvestComparison(new BigDecimal("1650000"),
        new BigDecimal("550000"), new BigDecimal("1100000"));
      assertThat(response.capitalGainsTax().totalTaxKrw()).isEqualByComparingTo("0");
      assertThat(response.capitalGainsTax().harvestComparison()).isEqualTo(expected);
      assertThat(sellAtEnd.capitalGainsTax().harvestComparison()).isEqualTo(expected);
      verify(portfolioEngine, times(5)).run(any());
      verify(backtestHistoryUtils).saveBacktestHistory("user123", BacktestType.STRATEGY_SIMULATION, request);
    }
  }

  @Nested
  @DisplayName("전략 등록")
  class StrategyRegistryTests {

    @Test
    @DisplayName("요청 타입의 전략이 등록되지 않았으면 전략 타입 불일치로 막는다")
    void runDcaStrategy_NoRegisteredStrategy_Rejected() {
      BacktestAnalysisServiceImpl onlyConditional = new BacktestAnalysisServiceImpl(tradingSimulationService,
        List.of(conditionalPurchaseStrategy), responseMapper, backtestHistoryUtils, portfolioEngine);
      DcaStrategyRequest request = DcaStrategyRequest.builder().symbol("AAPL").startDate(LocalDate.of(2023, 1, 1))
        .endDate(LocalDate.of(2024, 1, 1)).amountPerPurchase(BigDecimal.valueOf(1000)).purchaseDay(1).userId("user123").build();

      assertThatThrownBy(() -> onlyConditional.runDcaStrategy(request))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.STRATEGY_TYPE_MISMATCH);
      verify(backtestHistoryUtils, never()).saveBacktestHistory(any(), any(), any());
    }
  }
}
