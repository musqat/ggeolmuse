package com.muscat.backtest.domain.service.impl;

import com.muscat.backtest.common.calculation.ComparisonCalculationResult;
import com.muscat.backtest.common.calculation.ComparisonCalculator;
import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.enums.type.BacktestType;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.util.BacktestHistoryUtils;
import com.muscat.backtest.domain.dto.request.BaseComparisonRequest;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.request.OptimalTimingRequest;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
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
import com.muscat.backtest.domain.service.BacktestAnalysisService;
import com.muscat.backtest.domain.service.TradingSimulationService;
import com.muscat.backtest.domain.strategy.InvestmentStrategy;
import com.muscat.backtest.domain.strategy.OptimalTimingStrategy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class BacktestAnalysisServiceImpl implements BacktestAnalysisService {

  private final TradingSimulationService tradingSimulationService;
  private final Map<StrategyType, InvestmentStrategy> strategyMap;
  private final ResponseMapper responseMapper;
  private final BacktestHistoryUtils backtestHistoryUtils;
  private final OptimalTimingStrategy optimalTimingStrategy;

  public BacktestAnalysisServiceImpl(TradingSimulationService tradingSimulationService,
    List<InvestmentStrategy> strategies, ResponseMapper responseMapper,
    BacktestHistoryUtils backtestHistoryUtils, OptimalTimingStrategy optimalTimingStrategy) {
    this.tradingSimulationService = tradingSimulationService;
    this.responseMapper = responseMapper;
    this.backtestHistoryUtils = backtestHistoryUtils;
    this.optimalTimingStrategy = optimalTimingStrategy;
    // this.backtestEventProducer = backtestEventProducer;
    this.strategyMap = strategies.stream()
      .collect(Collectors.toMap(InvestmentStrategy::getStrategyType, Function.identity()));
  }

  // DCA 전략 백테스팅 실행
  @Override
  public StrategyResponse runDcaStrategy(DcaStrategyRequest request) {
    String backtestId = generateBacktestId();
    long startTime = System.currentTimeMillis();

    try {
      log.info("DCA 전략 백테스팅 시작: backtestId={}, symbol={}", backtestId, request.getSymbol());

      InvestmentStrategy strategy = getStrategy(StrategyType.DCA, "DCA 전략을 찾을 수 없습니다");
      StrategyResponse result = strategy.executeDca(request);

      saveBacktestHistory(request.getUserId(), BacktestType.STRATEGY_SIMULATION, request);

      long executionTimeMs = System.currentTimeMillis() - startTime;
      log.info("DCA 전략 백테스팅 완료: backtestId={}, 수익률={}%, 실행시간={}ms",
        backtestId, result.getTotalReturnPercent(), executionTimeMs);

      return result;

    } catch (Exception e) {
      long executionTimeMs = System.currentTimeMillis() - startTime;
      log.error("DCA 전략 백테스팅 실패: backtestId={}, error={}", backtestId, e.getMessage());
      throw e;
    }
  }

  @Override
  public StrategyResponse runConditionalStrategy(ConditionalStrategyRequest request) {
    String backtestId = generateBacktestId();
    long startTime = System.currentTimeMillis();

    try {
      log.info("조건부 매수 전략 백테스팅 시작: backtestId={}, symbol={}", backtestId, request.getSymbol());

      InvestmentStrategy strategy = getStrategy(StrategyType.CONDITIONAL_PURCHASE,
        "조건부 매수 전략을 찾을 수 없습니다");
      StrategyResponse result = strategy.executeConditional(request);

      saveBacktestHistory(request.getUserId(), BacktestType.STRATEGY_SIMULATION, request);

      long executionTimeMs = System.currentTimeMillis() - startTime;
      log.info("조건부 매수 전략 백테스팅 완료: backtestId={}, 수익률={}%, 실행시간={}ms",
        backtestId, result.getTotalReturnPercent(), executionTimeMs);

      return result;

    } catch (Exception e) {
      long executionTimeMs = System.currentTimeMillis() - startTime;
      log.error("조건부 매수 전략 백테스팅 실패: backtestId={}, error={}", backtestId, e.getMessage());
      throw e;
    }
  }

  // 여러 종목의 동일 기간 투자 성과를 비교 분석
  @Override
  public ComparisonResponse compareSymbols(SymbolComparisonRequest request) {
    String backtestId = generateBacktestId();
    long startTime = System.currentTimeMillis();

    try {
      log.info("종목 비교 분석 시작: backtestId={}, symbols={}", backtestId, request.getSymbols());

      List<ComparisonItem> items = collectComparisonItems(
        request.getSymbols(),
        symbol -> {
          SimulationRequest simulationRequest = buildSymbolSimulationRequest(request, symbol);
          SimulationResponse result = tradingSimulationService.runSimulation(simulationRequest,
            false);
          return responseMapper.toComparisonItemFromSimulation(result, symbol);
        },
        (symbol, e) -> log.warn("종목 {} 시뮬레이션 실패: {}", symbol, e.getMessage())
      );

      ComparisonResponse response = buildComparisonResponse(request, items,
        "모든 종목의 데이터를 찾을 수 없습니다", "종목 비교");
      recordComparisonHistory(request);

      if (response.getBestPerformer() != null) {
        long executionTimeMs = System.currentTimeMillis() - startTime;
        log.info("종목 비교 분석 완료: backtestId={}, 최고수익률={}%, 실행시간={}ms",
          backtestId, response.getBestPerformer().getTotalReturnPercent(), executionTimeMs);
      }

      return response;

    } catch (Exception e) {
      long executionTimeMs = System.currentTimeMillis() - startTime;
      log.error("종목 비교 분석 실패: backtestId={}, error={}", backtestId, e.getMessage());
      throw e;
    }
  }

  // 동일 종목에 대한 다양한 투자 전략의 성과를 비교 분석
  @Override
  public ComparisonResponse compareStrategies(StrategyComparisonRequest request) {
    log.info("전략 비교 분석: {} - {}개 전략", request.getSymbol(),
      request.getStrategies() != null ? request.getStrategies().size() : 0);

    List<ComparisonItem> items = collectComparisonItems(
      request.getStrategies(),
      strategyConfig -> buildStrategyComparisonItem(request, strategyConfig),
      (strategyConfig, e) -> log.warn("전략 {} 실행 실패: {}",
        strategyConfig.getName() != null ? strategyConfig.getName()
          : strategyConfig.getStrategyType(),
        e.getMessage())
    );

    ComparisonResponse response = buildComparisonResponse(request, items,
      "모든 전략의 실행에 실패했습니다", "전략 비교");
    recordComparisonHistory(request);
    return response;
  }

  // 동일 종목의 서로 다른 매수 시점들의 성과를 비교 분석
  @Override
  public ComparisonResponse compareTiming(TimingComparisonRequest request) {
    log.info("타이밍 비교 분석: {} - {}개 시점", request.getSymbol(),
      request.getPurchaseDates() != null ? request.getPurchaseDates().size() : 0);

    List<ComparisonItem> items = collectComparisonItems(
      request.getPurchaseDates(),
      purchaseDate -> {
        SimulationRequest simulationRequest = buildTimingSimulationRequest(request, purchaseDate);
        SimulationResponse result = tradingSimulationService.runSimulation(simulationRequest,
          false);
        String timingName = String.format("%s 매수", purchaseDate);
        return responseMapper.toComparisonItemFromSimulation(result, timingName);
      },
      (purchaseDate, e) -> log.warn("타이밍 {} 시뮬레이션 실패: {}", purchaseDate, e.getMessage())
    );

    ComparisonResponse response = buildComparisonResponse(request, items,
      "타이밍의 데이터를 찾을 수 없습니다", "타이밍 비교");
    recordComparisonHistory(request);
    return response;
  }

  private InvestmentStrategy getStrategy(StrategyType strategyType, String errorMessage) {
    InvestmentStrategy strategy = strategyMap.get(strategyType);
    if (strategy == null) {
      throw new BacktestException(BacktestResponse.STRATEGY_TYPE_MISMATCH, errorMessage);
    }
    return strategy;
  }

  private void saveBacktestHistory(String userId, BacktestType backtestType,
    Object request) {
    try {
      backtestHistoryUtils.saveBacktestHistory(userId, backtestType, request);
    } catch (Exception e) {
      log.warn("백테스트 히스토리 저장 실패: {}", e.getMessage());
    }
  }

  private ComparisonResponse buildComparisonResponse(BaseComparisonRequest request,
    List<ComparisonItem> items, String emptyDataMessage, String calculationType) {
    if (items.isEmpty()) {
      throw new BacktestException(BacktestResponse.DATA_NOT_FOUND, emptyDataMessage);
    }

    ComparisonCalculationResult calculation = ComparisonCalculator.calculate(items,
      calculationType);
    return responseMapper.toComparisonResponse(request, items, calculation);
  }

  private SimulationRequest buildSymbolSimulationRequest(SymbolComparisonRequest request,
    String symbol) {
    return SimulationRequest.builder()
      .symbol(symbol)
      .purchaseDate(request.getStartDate())
      .investmentAmount(request.getInvestmentAmount())
      .userId(request.getUserId())
      .build();
  }

  private SimulationRequest buildTimingSimulationRequest(TimingComparisonRequest request,
    LocalDate purchaseDate) {
    return SimulationRequest.builder()
      .symbol(request.getSymbol())
      .purchaseDate(purchaseDate)
      .investmentAmount(request.getInvestmentAmount())
      .userId(request.getUserId())
      .build();
  }

  private ComparisonItem buildStrategyComparisonItem(StrategyComparisonRequest request,
    StrategyParameter strategyConfig) {
    StrategyType strategyType = strategyConfig.getStrategyType();
    String strategyName = strategyConfig.getName() != null ? strategyConfig.getName()
      : strategyType != null ? strategyType.name() : "UNKNOWN";

    if (strategyType == StrategyType.DCA) {
      DcaStrategyRequest dcaRequest = DcaStrategyRequest.builder()
        .symbol(request.getSymbol())
        .startDate(request.getStartDate())
        .endDate(request.getEndDate())
        .userId(request.getUserId())
        .monthlyAmount(strategyConfig.getMonthlyAmount())
        .purchaseDay(strategyConfig.getPurchaseDay())
        .build();

      InvestmentStrategy strategy = getStrategy(StrategyType.DCA, "DCA 전략을 찾을 수 없습니다");
      StrategyResponse result = strategy.executeDca(dcaRequest);
      return responseMapper.toComparisonItemFromStrategy(result, strategyName);
    }

    if (strategyType == StrategyType.CONDITIONAL_PURCHASE) {
      ConditionalStrategyRequest conditionalRequest = ConditionalStrategyRequest.builder()
        .symbol(request.getSymbol())
        .startDate(request.getStartDate())
        .endDate(request.getEndDate())
        .userId(request.getUserId())
        .totalInvestment(strategyConfig.getTotalInvestment())
        .dropPercentage(strategyConfig.getDropPercentage())
        .maxPurchases(strategyConfig.getMaxPurchases())
        .build();

      InvestmentStrategy strategy = getStrategy(StrategyType.CONDITIONAL_PURCHASE,
        "조건부 매수 전략을 찾을 수 없습니다");
      StrategyResponse result = strategy.executeConditional(conditionalRequest);
      return responseMapper.toComparisonItemFromStrategy(result, strategyName);
    }

    SimulationRequest simulationRequest = SimulationRequest.builder()
      .symbol(request.getSymbol())
      .purchaseDate(strategyConfig.getPurchaseDate())
      .investmentAmount(request.getInvestmentAmount())
      .userId(request.getUserId())
      .build();

    SimulationResponse result = tradingSimulationService.runSimulation(simulationRequest,
      false);
    return responseMapper.toComparisonItemFromSimulation(result, strategyName);
  }

  private <T> List<ComparisonItem> collectComparisonItems(List<T> sources,
    Function<T, ComparisonItem> itemProvider, BiConsumer<T, Exception> errorHandler) {
    List<ComparisonItem> items = new ArrayList<>();
    for (T source : sources) {
      try {
        ComparisonItem item = itemProvider.apply(source);
        if (item != null) {
          items.add(item);
        }
      } catch (Exception e) {
        errorHandler.accept(source, e);
      }
    }
    return items;
  }

  private void recordComparisonHistory(BaseComparisonRequest request) {
    if (request.getUserId() == null) {
      return;
    }
    saveBacktestHistory(request.getUserId(), BacktestType.COMPARISON, request);
  }

  @Override
  public OptimalTimingResponse analyzeOptimalTiming(OptimalTimingRequest request) {
    log.info("최적 타이밍 분석 시작: {} - 목표 수익률 {}%",
      request.getSymbol(), request.getTargetReturnPercent());

    OptimalTimingResponse result = optimalTimingStrategy.analyzeOptimalTiming(request);

    if (request.getUserId() != null) {
      saveBacktestHistory(request.getUserId(), BacktestType.STRATEGY_SIMULATION, request);
    }

    log.info("최적 타이밍 분석 완료: {} - {}일 중 {}일 목표 달성",
      request.getSymbol(), result.getTotalAnalyzedDays(), result.getTotalQualifyingDays());

    return result;
  }

  //백테스트 ID 생성 헬퍼 메서드
  private String generateBacktestId() {
    return "BT_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }

}
