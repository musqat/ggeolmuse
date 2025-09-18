package com.muscat.backtest.domain.service.impl;

import com.muscat.backtest.common.calculation.ComparisonCalculationResult;
import com.muscat.backtest.common.calculation.ComparisonCalculator;
import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.util.BacktestHistoryUtils;
import com.muscat.backtest.common.enums.type.BacktestType;
import com.muscat.backtest.domain.dto.request.BaseComparisonRequest;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.request.StrategyComparisonRequest;
import com.muscat.backtest.domain.dto.request.SymbolComparisonRequest;
import com.muscat.backtest.domain.dto.request.TimingComparisonRequest;
import com.muscat.backtest.domain.dto.response.ComparisonResponse;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.entity.BacktestHistory;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.ComparisonItem;
import com.muscat.backtest.domain.model.StrategyParameter;
import com.muscat.backtest.domain.service.BacktestAnalysisService;
import com.muscat.backtest.domain.service.TradingSimulationService;
import com.muscat.backtest.domain.strategy.InvestmentStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

  public BacktestAnalysisServiceImpl(TradingSimulationService tradingSimulationService,
      List<InvestmentStrategy> strategies, ResponseMapper responseMapper,
      BacktestHistoryUtils backtestHistoryUtils) {
    this.tradingSimulationService = tradingSimulationService;
    this.responseMapper = responseMapper;
    this.backtestHistoryUtils = backtestHistoryUtils;
    this.strategyMap = strategies.stream()
        .collect(Collectors.toMap(InvestmentStrategy::getStrategyType, Function.identity()));
  }

  // 투자 전략 유형에 따라 백테스팅을 실행
  @Override
  public StrategyResponse runDcaStrategy(DcaStrategyRequest request) {
    log.info("DCA 전략 백테스팅 시작: {}", request.getSymbol());

    InvestmentStrategy strategy = getStrategy(StrategyType.DCA, "DCA 전략을 찾을 수 없습니다");
    StrategyResponse result = strategy.executeDca(request);

    saveBacktestHistory(request.getUserId(), BacktestType.STRATEGY_SIMULATION, request);
    return result;
  }

  @Override
  public StrategyResponse runConditionalStrategy(ConditionalStrategyRequest request) {
    log.info("조건부 매수 전략 백테스팅 시작: {}", request.getSymbol());

    InvestmentStrategy strategy = getStrategy(StrategyType.CONDITIONAL_PURCHASE,
        "조건부 매수 전략을 찾을 수 없습니다");
    StrategyResponse result = strategy.executeConditional(request);

    saveBacktestHistory(request.getUserId(), BacktestType.STRATEGY_SIMULATION, request);
    return result;
  }

  // 여러 종목의 동일 기간 투자 성과를 비교 분석
  @Override
  public ComparisonResponse compareSymbols(SymbolComparisonRequest request) {
    log.info("종목 비교 분석: {}", request.getSymbols());

    List<ComparisonItem> items = new ArrayList<>();

    // 각 종목에 대해 시뮬레이션 실행
    for (String symbol : request.getSymbols()) {
      SimulationRequest simulationRequest = SimulationRequest.builder().symbol(symbol)
          .purchaseDate(request.getStartDate()).investmentAmount(request.getInvestmentAmount())
          .userId(request.getUserId()).build();

      try {
        SimulationResponse result = tradingSimulationService.runSimulation(simulationRequest);
        ComparisonItem item = responseMapper.toComparisonItemFromSimulation(result, symbol);
        items.add(item);
      } catch (Exception e) {
        log.warn("종목 {} 시뮬레이션 실패: {}", symbol, e.getMessage());
      }
    }

    return buildComparisonResponse(request, items, "모든 종목의 데이터를 찾을 수 없습니다", "종목 비교");
  }

  // 동일 종목에 대한 다양한 투자 전략의 성과를 비교 분석
  @Override
  public ComparisonResponse compareStrategies(StrategyComparisonRequest request) {
    log.info("전략 비교 분석: {} - {}개 전략", request.getSymbol(),
        request.getStrategies() != null ? request.getStrategies().size() : 0);

    List<ComparisonItem> items = new ArrayList<>();

    // 각 전략에 대해 백테스팅 실행
    for (StrategyParameter strategyConfig : request.getStrategies()) {
      String strategyName = strategyConfig.getName() != null ? strategyConfig.getName()
          : strategyConfig.getStrategyType().name();

      try {
        if (strategyConfig.getStrategyType() == StrategyType.DCA) {
          DcaStrategyRequest dcaRequest = DcaStrategyRequest.builder().symbol(request.getSymbol())
              .startDate(request.getStartDate()).endDate(request.getEndDate())
              .userId(request.getUserId()).monthlyAmount(strategyConfig.getMonthlyAmount())
              .purchaseDay(strategyConfig.getPurchaseDay()).build();

          StrategyResponse result = runDcaStrategy(dcaRequest);
          ComparisonItem item = responseMapper.toComparisonItemFromStrategy(result, strategyName);
          item.setCategory("STRATEGY");
          items.add(item);

        } else if (strategyConfig.getStrategyType() == StrategyType.CONDITIONAL_PURCHASE) {
          ConditionalStrategyRequest conditionalRequest = ConditionalStrategyRequest.builder()
              .symbol(request.getSymbol()).startDate(request.getStartDate())
              .endDate(request.getEndDate()).userId(request.getUserId())
              .totalInvestment(strategyConfig.getTotalInvestment())
              .dropPercentage(strategyConfig.getDropPercentage())
              .maxPurchases(strategyConfig.getMaxPurchases()).build();

          StrategyResponse result = runConditionalStrategy(conditionalRequest);
          ComparisonItem item = responseMapper.toComparisonItemFromStrategy(result, strategyName);
          item.setCategory("STRATEGY");
          items.add(item);

        } else {
          // 일시불 (시뮬레이션) 전략
          SimulationRequest simulationRequest = SimulationRequest.builder()
              .symbol(request.getSymbol()).purchaseDate(strategyConfig.getPurchaseDate())
              .investmentAmount(request.getInvestmentAmount()).userId(request.getUserId()).build();

          SimulationResponse result = tradingSimulationService.runSimulation(simulationRequest);
          ComparisonItem item = responseMapper.toComparisonItemFromSimulation(result, strategyName);
          item.setCategory("STRATEGY");
          items.add(item);
        }
      } catch (Exception e) {
        log.warn("전략 {} 실행 실패: {}", strategyName, e.getMessage());
      }
    }

    return buildComparisonResponse(request, items, "모든 전략의 실행에 실패했습니다", "전략 비교");
  }

  // 동일 종목의 서로 다른 매수 시점들의 성과를 비교 분석
  @Override
  public ComparisonResponse compareTiming(TimingComparisonRequest request) {
    log.info("타이밍 비교 분석: {} - {}개 시점", request.getSymbol(),
        request.getPurchaseDates() != null ? request.getPurchaseDates().size() : 0);

    List<ComparisonItem> items = new ArrayList<>();

    // 각 매수 시점에 대해 시뮬레이션 실행
    for (int i = 0; i < request.getPurchaseDates().size(); i++) {
      SimulationRequest simulationRequest = SimulationRequest.builder().symbol(request.getSymbol())
          .purchaseDate(request.getPurchaseDates().get(i))
          .investmentAmount(request.getInvestmentAmount()).userId(request.getUserId()).build();

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

    return buildComparisonResponse(request, items, "타이밍의 데이터를 찾을 수 없습니다", "타이밍 비교");
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

}