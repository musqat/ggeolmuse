package com.muscat.backtest.domain.service.impl;

import com.muscat.backtest.domain.calculation.ComparisonCalculationResult;
import com.muscat.backtest.domain.calculation.ComparisonCalculator;
import com.muscat.backtest.domain.calculation.PortfolioCalculator;
import com.muscat.backtest.domain.engine.PortfolioEngine;
import com.muscat.backtest.domain.engine.YearEndHarvest;
import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.BacktestType;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.domain.service.BacktestHistoryUtils;
import com.muscat.backtest.domain.validation.BacktestRequestValidator;
import com.muscat.backtest.domain.validation.PortfolioRequestValidator;
import com.muscat.backtest.domain.validation.RoundLimit;
import com.muscat.backtest.domain.dto.request.BaseComparisonRequest;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.request.MaBandStrategyRequest;
import com.muscat.backtest.domain.dto.request.MaCrossStrategyRequest;
import com.muscat.backtest.domain.dto.request.PortfolioRequest;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.request.StrategyComparisonRequest;
import com.muscat.backtest.domain.dto.request.StrategyRequest;
import com.muscat.backtest.domain.dto.request.SymbolComparisonRequest;
import com.muscat.backtest.domain.dto.response.CapitalGainsTax;
import com.muscat.backtest.domain.dto.response.ComparisonResponse;
import com.muscat.backtest.domain.dto.response.PortfolioResponse;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.mapper.ComparisonRequestMapper;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.ComparisonItem;
import com.muscat.backtest.domain.model.StrategyParameter;
import com.muscat.backtest.domain.service.BacktestAnalysisService;
import com.muscat.backtest.domain.service.TradingSimulationService;
import com.muscat.backtest.domain.strategy.InvestmentStrategy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
  private final PortfolioEngine portfolioEngine;

  public BacktestAnalysisServiceImpl(TradingSimulationService tradingSimulationService,
                                     List<InvestmentStrategy> strategies, ResponseMapper responseMapper,
                                     BacktestHistoryUtils backtestHistoryUtils, PortfolioEngine portfolioEngine) {
    this.tradingSimulationService = tradingSimulationService;
    this.responseMapper = responseMapper;
    this.backtestHistoryUtils = backtestHistoryUtils;
    this.portfolioEngine = portfolioEngine;
    this.strategyMap = strategies.stream()
      .collect(Collectors.toMap(InvestmentStrategy::getStrategyType, Function.identity()));
  }

  @Override
  public StrategyResponse runDcaStrategy(DcaStrategyRequest request) {
    return runStrategy("DCA", request);
  }

  @Override
  public StrategyResponse runConditionalStrategy(ConditionalStrategyRequest request) {
    return runStrategy("조건부 매수", request);
  }

  @Override
  public StrategyResponse runMaCrossStrategy(MaCrossStrategyRequest request) {
    return runStrategy("이동평균 교차", request);
  }

  @Override
  public StrategyResponse runMaBandStrategy(MaBandStrategyRequest request) {
    return runStrategy("이동평균 밴드", request);
  }

  // 요청 검사 → 엔진 → 결과 계산 · 연말 정리 비교, 이력은 투자 전략 시뮬레이션으로 요청 JSON 저장
  @Override
  public PortfolioResponse runPortfolio(PortfolioRequest request) {
    long startTime = System.currentTimeMillis();
    PortfolioRequestValidator.validate(request);
    PortfolioResponse response = compareYearEnd(request, PortfolioCalculator.of(request, portfolioEngine.run(request)));
    backtestHistoryUtils.saveBacktestHistory(request.getUserId(), BacktestType.STRATEGY_SIMULATION, request);
    log.info("포트폴리오 백테스팅 완료: {}개 종목, 수익률={}%, 실행시간={}ms", request.getAssets().size(),
      response.totalReturnPercent(), System.currentTimeMillis() - startTime);
    return response;
  }

  // 포트폴리오 사본 요청으로 연말 정리 비교를 더한 결과
  private PortfolioResponse compareYearEnd(PortfolioRequest request, PortfolioResponse response) {
    if (!YearEndHarvest.compares(request.getCapitalGainsTax(), request.getYearEndHarvest(), request.getStartDate(),
      response.endDate())) {
      return response;
    }
    BigDecimal sellAtEndTax = portfolioTax(request.yearEndVariant(false));
    BigDecimal harvestTax = PeriodEnd.sells(request.getPeriodEnd())
      ? response.capitalGainsTax().totalTaxKrw()
      : portfolioTax(request.yearEndVariant(true));
    return response.toBuilder()
      .capitalGainsTax(response.capitalGainsTax().withHarvestComparison(
        CapitalGainsTax.HarvestComparison.of(sellAtEndTax, harvestTax)))
      .build();
  }

  // 사본 요청을 엔진에 다시 돌린 양도세 합
  private BigDecimal portfolioTax(PortfolioRequest variant) {
    return PortfolioCalculator.of(variant, portfolioEngine.run(variant)).capitalGainsTax().totalTaxKrw();
  }

  // 전략 실행 · 연말 정리 비교 · 이력 저장과 걸린 시간 로그
  private StrategyResponse runStrategy(String label, StrategyRequest request) {
    long startTime = System.currentTimeMillis();
    log.info("{} 전략 백테스팅 시작: symbol={}", label, request.getSymbol());
    try {
      StrategyResponse result = execute(request);
      compareYearEnd(request, result);
      backtestHistoryUtils.saveBacktestHistory(request.getUserId(), BacktestType.STRATEGY_SIMULATION, request);
      log.info("{} 전략 백테스팅 완료: 수익률={}%, 실행시간={}ms",
        label, result.getTotalReturnPercent(), System.currentTimeMillis() - startTime);
      return result;
    } catch (RuntimeException e) {
      log.error("{} 전략 백테스팅 실패: error={}, 실행시간={}ms",
        label, e.getMessage(), System.currentTimeMillis() - startTime);
      throw e;
    }
  }

  // 연말 정리를 켠 2년 이상 결과에 기간 끝에 한 번에 판 양도세와 연말 정리하고 기간 끝에 판 양도세 비교를 더함
  private void compareYearEnd(StrategyRequest request, StrategyResponse result) {
    LocalDate today = LocalDate.now();
    LocalDate valuationDate = request.getEndDate().isAfter(today) ? today : request.getEndDate();
    if (!YearEndHarvest.compares(request.getCapitalGainsTax(), request.getYearEndHarvest(), request.getStartDate(),
      valuationDate)) {
      return;
    }
    BigDecimal sellAtEndTax = execute(request.yearEndVariant(false)).getCapitalGainsTax().totalTaxKrw();
    BigDecimal harvestTax = PeriodEnd.sells(request.getPeriodEnd())
      ? result.getCapitalGainsTax().totalTaxKrw()
      : execute(request.yearEndVariant(true)).getCapitalGainsTax().totalTaxKrw();
    result.setCapitalGainsTax(result.getCapitalGainsTax().withHarvestComparison(
      CapitalGainsTax.HarvestComparison.of(sellAtEndTax, harvestTax)));
  }

  // 여러 종목의 동일 기간 투자 성과를 비교 분석
  @Override
  public ComparisonResponse compareSymbols(SymbolComparisonRequest request) {
    log.info("종목 비교 분석: symbols={}", request.getSymbols());
    request.getSymbols().forEach(BacktestRequestValidator::requireTradable);

    List<ComparisonItem> items = collectComparisonItems(
      request.getSymbols(),
      symbol -> {
        SimulationRequest simulationRequest = ComparisonRequestMapper.symbolSimulation(request, symbol);
        SimulationResponse result = tradingSimulationService.runSimulation(simulationRequest,
          false);
        return responseMapper.toComparisonItemFromSimulation(result, symbol);
      },
      (symbol, e) -> log.warn("종목 {} 시뮬레이션 실패: {}", symbol, e.getMessage())
    );

    ComparisonResponse response = buildComparisonResponse(request, items, BacktestResponse.ALL_SYMBOLS_DATA_NOT_FOUND);
    recordComparisonHistory(request);
    return response;
  }

  // 동일 종목의 투자 전략별 성과 비교 분석
  @Override
  public ComparisonResponse compareStrategies(StrategyComparisonRequest request) {
    log.info("전략 비교 분석: {} - {}개 전략", request.getSymbol(),
      request.getStrategies() != null ? request.getStrategies().size() : 0);
    BacktestRequestValidator.requireTradable(request.getSymbol());
    requireFirstPurchasesInPeriod(request);
    RoundLimit.requireComparison(roundChips(request), LocalDate.now());

    List<ComparisonItem> items = collectComparisonItems(
      request.getStrategies(),
      strategyConfig -> buildStrategyComparisonItem(request, strategyConfig),
      (strategyConfig, e) -> log.warn("전략 {} 실행 실패: {}",
        strategyConfig.getName() != null ? strategyConfig.getName()
          : strategyConfig.getStrategyType(),
        e.getMessage())
    );

    ComparisonResponse response = buildComparisonResponse(request, items, BacktestResponse.ALL_STRATEGIES_FAILED);
    recordComparisonHistory(request);
    return response;
  }

  // 적립식 칩 첫 매수일이 기간 밖이면 어느 전략도 돌리기 전에 비교 전체를 막음
  private static void requireFirstPurchasesInPeriod(StrategyComparisonRequest request) {
    request.getStrategies().stream()
      .filter(strategy -> strategy.getStrategyType() == StrategyType.DCA)
      .forEach(strategy -> BacktestRequestValidator.requireFirstPurchaseInPeriod(strategy.getFirstPurchaseDate(),
        request.getStartDate(), request.getEndDate()));
  }

  // 회차 일정이 있는 칩(적립식 · 조건부)의 전략 요청
  private static List<StrategyRequest> roundChips(StrategyComparisonRequest request) {
    return request.getStrategies().stream()
      .filter(strategy -> strategy.getStrategyType() == StrategyType.DCA
        || strategy.getStrategyType() == StrategyType.CONDITIONAL_PURCHASE)
      .map(strategy -> ComparisonRequestMapper.strategyRequest(request, strategy))
      .toList();
  }

  // 요청의 전략 타입에 맞는 전략으로 실행
  private StrategyResponse execute(StrategyRequest request) {
    InvestmentStrategy strategy = strategyMap.get(request.getStrategyType());
    if (strategy == null) {
      throw new BacktestException(BacktestResponse.STRATEGY_TYPE_MISMATCH);
    }
    return strategy.execute(request);
  }

  private ComparisonResponse buildComparisonResponse(BaseComparisonRequest request,
                                                     List<ComparisonItem> items, BacktestResponse emptyCode) {
    if (items.isEmpty()) {
      throw new BacktestException(emptyCode);
    }

    ComparisonCalculationResult calculation = ComparisonCalculator.calculate(items);
    return responseMapper.toComparisonResponse(request, items, calculation);
  }

  private ComparisonItem buildStrategyComparisonItem(StrategyComparisonRequest request,
                                                     StrategyParameter strategyConfig) {
    StrategyType strategyType = strategyConfig.getStrategyType();
    String strategyName = strategyConfig.getName() != null ? strategyConfig.getName()
      : strategyType != null ? strategyType.name() : "UNKNOWN";

    if (strategyType == StrategyType.SIMPLE) {
      SimulationRequest simulationRequest = ComparisonRequestMapper.simpleSimulation(request, strategyConfig);

      SimulationResponse result = tradingSimulationService.runSimulation(simulationRequest,
        false);
      return responseMapper.toComparisonItemFromSimulation(result, strategyName);
    }

    StrategyResponse result = execute(ComparisonRequestMapper.strategyRequest(request, strategyConfig));
    return responseMapper.toComparisonItemFromStrategy(result, strategyName);
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
      } catch (BacktestException e) {
        // 시세 서비스 장애는 남은 항목도 같은 결과라 비교 전체를 그 오류로 끝냄
        if (e.getCode() == BacktestResponse.MARKET_DATA_UNAVAILABLE) {
          throw e;
        }
        errorHandler.accept(source, e);
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
    backtestHistoryUtils.saveBacktestHistory(request.getUserId(), BacktestType.COMPARISON, request);
  }

}
