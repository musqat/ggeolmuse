package com.muscat.backtest.domain.service.impl;

import com.muscat.backtest.common.calculation.ComparisonCalculationResult;
import com.muscat.backtest.common.calculation.ComparisonCalculator;
import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.logging.BacktestLogger;
import com.muscat.backtest.domain.dto.request.BaseComparisonRequest;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.request.StrategyComparisonRequest;
import com.muscat.backtest.domain.dto.request.StrategyRequest;
import com.muscat.backtest.domain.dto.request.SymbolComparisonRequest;
import com.muscat.backtest.domain.dto.request.TimingComparisonRequest;
import com.muscat.backtest.domain.dto.response.ComparisonResponse;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.ComparisonItem;
import com.muscat.backtest.domain.model.StrategyParameter;
import com.muscat.backtest.domain.service.BacktestAnalysisService;
import com.muscat.backtest.domain.service.TradingSimulationService;
import com.muscat.backtest.domain.strategy.InvestmentStrategy;
import com.muscat.backtest.domain.repository.BacktestHistoryRepository;
import com.muscat.backtest.domain.entity.BacktestHistory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 백테스팅 전략 실행 및 비교 분석을 통합한 서비스 구현체
@Service
@Slf4j
public class BacktestAnalysisServiceImpl implements BacktestAnalysisService {

  private final TradingSimulationService tradingSimulationService;
  private final Map<StrategyType, InvestmentStrategy> strategyMap;
  private final ResponseMapper responseMapper;
  private final BacktestHistoryRepository backtestHistoryRepository;
  private final ObjectMapper objectMapper;

  public BacktestAnalysisServiceImpl(TradingSimulationService tradingSimulationService,
      List<InvestmentStrategy> strategies,
      ResponseMapper responseMapper,
      BacktestHistoryRepository backtestHistoryRepository,
      ObjectMapper objectMapper) {
    this.tradingSimulationService = tradingSimulationService;
    this.responseMapper = responseMapper;
    this.backtestHistoryRepository = backtestHistoryRepository;
    this.objectMapper = objectMapper;
    this.strategyMap = strategies.stream()
        .collect(Collectors.toMap(
            InvestmentStrategy::getStrategyType,
            Function.identity()
        ));
  }

  // 투자 전략 유형에 따라 백테스팅을 실행하고 결과를 반환합니다
  @Override
  public StrategyResponse runStrategy(StrategyRequest request) {
    log.info("전략 백테스팅 시작: {} - {}", request.getStrategyType(), request.getSymbol());

    InvestmentStrategy strategy = strategyMap.get(request.getStrategyType());
    if (strategy == null) {
      throw new BacktestException(BacktestResponse.STRATEGY_TYPE_MISMATCH,
          "지원하지 않는 전략 타입입니다: " + request.getStrategyType());
    }

    return strategy.execute(request);
  }

  // 여러 종목의 동일 기간 투자 성과를 비교 분석합니다
  @Override
  public ComparisonResponse compareSymbols(SymbolComparisonRequest request) {
    log.info("종목 비교 분석: {}", request.getSymbols());

    List<ComparisonItem> items = new ArrayList<>();

    // 각 종목에 대해 시뮬레이션 실행
    for (String symbol : request.getSymbols()) {
      SimulationRequest simulationRequest = SimulationRequest.builder()
          .symbol(symbol)
          .purchaseDate(request.getStartDate())
          .investmentAmount(request.getInvestmentAmount())
          .userId(request.getUserId())
          .build();

      try {
        SimulationResponse result = tradingSimulationService.runSimulation(simulationRequest);
        ComparisonItem item = responseMapper.toComparisonItemFromSimulation(result, symbol);
        items.add(item);
      } catch (Exception e) {
        log.warn("종목 {} 시뮬레이션 실패: {}", symbol, e.getMessage());
      }
    }

    if (items.isEmpty()) {
      throw new BacktestException(BacktestResponse.DATA_NOT_FOUND, "모든 종목의 데이터를 찾을 수 없습니다");
    }

    ComparisonCalculationResult calculation = ComparisonCalculator.calculate(items, "종목 비교");

    return responseMapper.toComparisonResponse(request, items, calculation);
  }

  // 동일 종목에 대한 다양한 투자 전략의 성과를 비교 분석합니다
  @Override
  public ComparisonResponse compareStrategies(StrategyComparisonRequest request) {
    log.info("전략 비교 분석: {} - {}개 전략", request.getSymbol(),
        request.getStrategies() != null ? request.getStrategies().size() : 0);

    List<ComparisonItem> items = new ArrayList<>();

    // 각 전략에 대해 백테스팅 실행
    for (StrategyParameter strategyConfig : request.getStrategies()) {
      String strategyName = strategyConfig.getName() != null ?
          strategyConfig.getName() : strategyConfig.getStrategyType().name();

      try {
        if (strategyConfig.getStrategyType() == StrategyType.DCA) {
          // DCA 전략 실행
          StrategyRequest strategyRequest = StrategyRequest.builder()
              .symbol(request.getSymbol())
              .startDate(request.getStartDate())
              .endDate(request.getEndDate())
              .strategyType(StrategyType.DCA)
              .monthlyAmount(strategyConfig.getMonthlyAmount())
              .investmentDay(strategyConfig.getInvestmentDay())
              .userId(request.getUserId())
              .build();

          StrategyResponse result = runStrategy(strategyRequest);
          ComparisonItem item = responseMapper.toComparisonItemFromStrategy(result,
              strategyName);
          items.add(item);

        } else if (strategyConfig.getStrategyType()
            == StrategyType.CONDITIONAL_PURCHASE) {
          // 조건부 매수 전략 실행
          StrategyRequest strategyRequest = StrategyRequest.builder()
              .symbol(request.getSymbol())
              .startDate(request.getStartDate())
              .endDate(request.getEndDate())
              .strategyType(StrategyType.CONDITIONAL_PURCHASE)
              .totalInvestment(request.getInvestmentAmount())
              .dropPercentage(strategyConfig.getDropPercentage())
              .maxPurchases(strategyConfig.getMaxPurchases())
              .userId(request.getUserId())
              .build();

          StrategyResponse result = runStrategy(strategyRequest);
          ComparisonItem item = responseMapper.toComparisonItemFromStrategy(result,
              strategyName);
          items.add(item);

        } else {
          // 일시불 (시뮬레이션) 전략
          SimulationRequest simulationRequest = SimulationRequest.builder()
              .symbol(request.getSymbol())
              .purchaseDate(strategyConfig.getPurchaseDate())
              .investmentAmount(request.getInvestmentAmount())
              .userId(request.getUserId())
              .build();

          SimulationResponse result = tradingSimulationService.runSimulation(simulationRequest);
          ComparisonItem item = responseMapper.toComparisonItemFromSimulation(result,
              strategyName);
          item.setCategory("STRATEGY");
          items.add(item);
        }
      } catch (Exception e) {
        log.warn("전략 {} 실행 실패: {}", strategyName, e.getMessage());
      }
    }

    if (items.isEmpty()) {
      throw new BacktestException(BacktestResponse.DATA_NOT_FOUND, "모든 전략의 실행에 실패했습니다");
    }

    ComparisonCalculationResult calculation = ComparisonCalculator.calculate(items, "전략 비교");

    return responseMapper.toComparisonResponse(request, items, calculation);
  }

  // 동일 종목의 서로 다른 매수 시점들의 성과를 비교 분석합니다
  @Override
  public ComparisonResponse compareTiming(TimingComparisonRequest request) {
    log.info("타이밍 비교 분석: {} - {}개 시점", request.getSymbol(),
        request.getPurchaseDates() != null ? request.getPurchaseDates().size() : 0);

    List<ComparisonItem> items = new ArrayList<>();

    // 각 매수 시점에 대해 시뮬레이션 실행
    for (int i = 0; i < request.getPurchaseDates().size(); i++) {
      SimulationRequest simulationRequest = SimulationRequest.builder()
          .symbol(request.getSymbol())
          .purchaseDate(request.getPurchaseDates().get(i))
          .investmentAmount(request.getInvestmentAmount())
          .userId(request.getUserId())
          .build();

      try {
        SimulationResponse result = tradingSimulationService.runSimulation(simulationRequest);
        String timingName = String.format("%s 매수", request.getPurchaseDates().get(i));
        ComparisonItem item = responseMapper.toComparisonItemFromSimulation(result, timingName);
        item.setCategory("TIMING");
        items.add(item);
      } catch (Exception e) {
        log.warn("타이밍 {} 시뮬레이션 실패: {}", request.getPurchaseDates().get(i), e.getMessage());
      }
    }

    if (items.isEmpty()) {
      throw new BacktestException(BacktestResponse.DATA_NOT_FOUND, "타이밍의 데이터를 찾을 수 없습니다");
    }

    ComparisonCalculationResult calculation = ComparisonCalculator.calculate(items, "타이밍 비교");

    return responseMapper.toComparisonResponse(request, items, calculation);
  }

  // 비교 유형에 따라 종목, 전략, 또는 타이밍 비교 분석을 실행합니다
  @Override
  public ComparisonResponse runComparison(BaseComparisonRequest request) {
    // MDC 컨텍스트 설정
    BacktestLogger.setAnalysisContext(request.getUserId(), request.getComparisonType().name());

    log.info("비교 분석 시작: {}", request.getComparisonType());

    try {
      ComparisonResponse result = switch (request.getComparisonType()) {
        case SYMBOLS -> {
          if (!(request instanceof SymbolComparisonRequest)) {
            throw new BacktestException(BacktestResponse.COMPARISON_REQUEST_TYPE_MISMATCH);
          }
          yield compareSymbols((SymbolComparisonRequest) request);
        }
        case STRATEGIES -> {
          if (!(request instanceof StrategyComparisonRequest)) {
            throw new BacktestException(BacktestResponse.COMPARISON_REQUEST_TYPE_MISMATCH);
          }
          yield compareStrategies((StrategyComparisonRequest) request);
        }
        case TIMING -> {
          if (!(request instanceof TimingComparisonRequest)) {
            throw new BacktestException(BacktestResponse.COMPARISON_REQUEST_TYPE_MISMATCH);
          }
          yield compareTiming((TimingComparisonRequest) request);
        }
      };

      // 백테스트 히스토리 기록
      BacktestHistory.BacktestType historyType = switch (request.getComparisonType()) {
        case SYMBOLS -> BacktestHistory.BacktestType.SYMBOL_COMPARISON;
        case STRATEGIES -> BacktestHistory.BacktestType.STRATEGY_COMPARISON;
        case TIMING -> BacktestHistory.BacktestType.TIMING_COMPARISON;
      };
      saveBacktestHistory(request.getUserId(), historyType, request);
      
      return result;
    } finally {
      BacktestLogger.remove("operation");
    }
  }

  private void saveBacktestHistory(String userId, BacktestHistory.BacktestType backtestType, Object requestParams) {
    executeWithFallback(() -> {
      try {
        String paramsJson = objectMapper.writeValueAsString(requestParams);
        BacktestHistory history = BacktestHistory.builder()
            .userId(userId)
            .backtestType(backtestType)
            .requestParams(paramsJson)
            .build();
        backtestHistoryRepository.save(history);
        log.debug("백테스트 히스토리 저장 완료: userId={}, type={}", userId, backtestType);
        return null;
      } catch (JsonProcessingException e) {
        log.warn("JSON 변환 오류: userId={}, error={}", userId, e.getMessage());
        return null;
      }
    }, "백테스트 히스토리 저장", userId);
  }

  private void executeWithFallback(Supplier<Void> operation, String operationName, String userId) {
    try {
      operation.get();
    } catch (Exception e) {
      log.warn("{} 실패: userId={}, error={}", operationName, userId, e.getMessage());
    }
  }

}