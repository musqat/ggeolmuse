package com.muscat.backtest.domain.service.impl;

import com.muscat.backtest.common.calculation.ComparisonCalculationResult;
import com.muscat.backtest.common.calculation.ComparisonCalculator;
import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.BacktestType;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.util.BacktestHistoryUtils;
import com.muscat.backtest.common.validation.BacktestRequestValidator;
import com.muscat.backtest.domain.dto.request.BaseComparisonRequest;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.request.MaBandStrategyRequest;
import com.muscat.backtest.domain.dto.request.MaCrossStrategyRequest;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.request.StrategyComparisonRequest;
import com.muscat.backtest.domain.dto.request.StrategyRequest;
import com.muscat.backtest.domain.dto.request.SymbolComparisonRequest;
import com.muscat.backtest.domain.dto.response.ComparisonResponse;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.ComparisonItem;
import com.muscat.backtest.domain.model.StrategyParameter;
import com.muscat.backtest.domain.service.BacktestAnalysisService;
import com.muscat.backtest.domain.service.TradingSimulationService;
import com.muscat.backtest.domain.strategy.InvestmentStrategy;
import java.math.BigDecimal;
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

  public BacktestAnalysisServiceImpl(TradingSimulationService tradingSimulationService,
    List<InvestmentStrategy> strategies, ResponseMapper responseMapper,
    BacktestHistoryUtils backtestHistoryUtils) {
    this.tradingSimulationService = tradingSimulationService;
    this.responseMapper = responseMapper;
    this.backtestHistoryUtils = backtestHistoryUtils;
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

  // 전략 실행 · 이력 저장과 걸린 시간 로그
  private StrategyResponse runStrategy(String label, StrategyRequest request) {
    long startTime = System.currentTimeMillis();
    log.info("{} 전략 백테스팅 시작: symbol={}", label, request.getSymbol());
    try {
      StrategyResponse result = execute(request);
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

  // 여러 종목의 동일 기간 투자 성과를 비교 분석
  @Override
  public ComparisonResponse compareSymbols(SymbolComparisonRequest request) {
    log.info("종목 비교 분석: symbols={}", request.getSymbols());
    request.getSymbols().forEach(BacktestRequestValidator::requireTradable);

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
      "모든 종목의 데이터를 찾을 수 없습니다");
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

    List<ComparisonItem> items = collectComparisonItems(
      request.getStrategies(),
      strategyConfig -> buildStrategyComparisonItem(request, strategyConfig),
      (strategyConfig, e) -> log.warn("전략 {} 실행 실패: {}",
        strategyConfig.getName() != null ? strategyConfig.getName()
          : strategyConfig.getStrategyType(),
        e.getMessage())
    );

    ComparisonResponse response = buildComparisonResponse(request, items,
      "모든 전략의 실행에 실패했습니다");
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

  // 요청의 전략 타입에 맞는 전략으로 실행
  private StrategyResponse execute(StrategyRequest request) {
    InvestmentStrategy strategy = strategyMap.get(request.getStrategyType());
    if (strategy == null) {
      throw new BacktestException(BacktestResponse.STRATEGY_TYPE_MISMATCH,
        "등록된 전략이 없습니다: " + request.getStrategyType());
    }
    return strategy.execute(request);
  }

  private ComparisonResponse buildComparisonResponse(BaseComparisonRequest request,
    List<ComparisonItem> items, String emptyDataMessage) {
    if (items.isEmpty()) {
      throw new BacktestException(BacktestResponse.DATA_NOT_FOUND, emptyDataMessage);
    }

    ComparisonCalculationResult calculation = ComparisonCalculator.calculate(items);
    return responseMapper.toComparisonResponse(request, items, calculation);
  }

  private SimulationRequest buildSymbolSimulationRequest(SymbolComparisonRequest request,
    String symbol) {
    return simulationWithOptions(request)
      .symbol(symbol)
      .purchaseDate(request.getStartDate())
      .build();
  }

  // 비교 요청의 매도일(종료일) · 투자금 · 수수료 · 환율 · 배당 · 양도세 · 기간 끝 옵션으로 채운 단순 시뮬레이션 요청
  private SimulationRequest.SimulationRequestBuilder simulationWithOptions(
    BaseComparisonRequest request) {
    return SimulationRequest.builder()
      .saleDate(request.getEndDate())
      .investmentAmount(request.getInvestmentAmount())
      .userId(request.getUserId())
      .tradingFeeRate(orZero(request.getTradingFeeRate()))
      .purchaseFxRate(request.getPurchaseFxRate())
      .currentFxRate(request.getCurrentFxRate())
      .reinvestDividends(reinvests(request))
      .dividendTaxRate(orZero(request.getDividendTaxRate()))
      .capitalGainsTax(request.getCapitalGainsTax())
      .periodEnd(request.getPeriodEnd());
  }

  // 빈 값은 재투자 안 함
  private static boolean reinvests(BaseComparisonRequest request) {
    return Boolean.TRUE.equals(request.getReinvestDividends());
  }

  // 빈 값은 0(builder 에 null 을 넣으면 @Builder.Default 가 덮임)
  private static BigDecimal orZero(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
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
        .amountPerPurchase(strategyConfig.getAmountPerPurchase())
        .frequency(strategyConfig.getFrequency())
        .firstPurchaseDate(strategyConfig.getFirstPurchaseDate())
        .purchaseDay(strategyConfig.getPurchaseDay())
        .investmentInterval(strategyConfig.getInvestmentInterval() != null
          ? strategyConfig.getInvestmentInterval() : 1)
        .totalInvestmentLimit(request.getInvestmentAmount())
        .purchaseFxRate(request.getPurchaseFxRate())
        .currentFxRate(request.getCurrentFxRate())
        .reinvestDividends(reinvests(request))
        .dividendTaxRate(orZero(request.getDividendTaxRate()))
        .tradingFeeRate(orZero(request.getTradingFeeRate()))
        .capitalGainsTax(request.getCapitalGainsTax())
        .periodEnd(request.getPeriodEnd())
        .build();

      StrategyResponse result = execute(dcaRequest);
      return responseMapper.toComparisonItemFromStrategy(result, strategyName);
    }

    if (strategyType == StrategyType.CONDITIONAL_PURCHASE) {
      boolean sells = strategyConfig.getTakeProfitRate() != null || strategyConfig.getStopLossRate() != null;
      ConditionalStrategyRequest conditionalRequest = ConditionalStrategyRequest.builder()
        .symbol(request.getSymbol())
        .startDate(request.getStartDate())
        .endDate(request.getEndDate())
        .userId(request.getUserId())
        .amountPerPurchase(strategyConfig.getAmountPerPurchase())
        .totalInvestment(request.getInvestmentAmount())
        .dropPercentage(strategyConfig.getDropPercentage())
        .direction(strategyConfig.getDirection())
        .maxPurchases(strategyConfig.getMaxPurchases())
        .takeProfitRate(strategyConfig.getTakeProfitRate())
        .stopLossRate(strategyConfig.getStopLossRate())
        .purchaseFxRate(request.getPurchaseFxRate())
        .currentFxRate(request.getCurrentFxRate())
        // 익절 · 손절을 켠 칩만 재투자 끔
        .reinvestDividends(!sells && reinvests(request))
        .dividendTaxRate(orZero(request.getDividendTaxRate()))
        .tradingFeeRate(orZero(request.getTradingFeeRate()))
        .capitalGainsTax(request.getCapitalGainsTax())
        .periodEnd(request.getPeriodEnd())
        .build();

      StrategyResponse result = execute(conditionalRequest);
      return responseMapper.toComparisonItemFromStrategy(result, strategyName);
    }

    if (strategyType == StrategyType.MA_CROSS) {
      MaCrossStrategyRequest maCrossRequest = MaCrossStrategyRequest.builder()
        .symbol(request.getSymbol())
        .startDate(request.getStartDate())
        .endDate(request.getEndDate())
        .userId(request.getUserId())
        .investmentAmount(request.getInvestmentAmount())
        .shortWindow(strategyConfig.getShortWindow())
        .longWindow(strategyConfig.getLongWindow())
        .purchaseFxRate(request.getPurchaseFxRate())
        .currentFxRate(request.getCurrentFxRate())
        // 교차마다 전량 팔아 재투자 끔
        .reinvestDividends(false)
        .dividendTaxRate(orZero(request.getDividendTaxRate()))
        .tradingFeeRate(orZero(request.getTradingFeeRate()))
        .capitalGainsTax(request.getCapitalGainsTax())
        .periodEnd(request.getPeriodEnd())
        .build();

      StrategyResponse result = execute(maCrossRequest);
      return responseMapper.toComparisonItemFromStrategy(result, strategyName);
    }

    if (strategyType == StrategyType.MA_BAND) {
      MaBandStrategyRequest maBandRequest = MaBandStrategyRequest.builder()
        .symbol(request.getSymbol())
        .startDate(request.getStartDate())
        .endDate(request.getEndDate())
        .userId(request.getUserId())
        .investmentAmount(request.getInvestmentAmount())
        .bandWindow(strategyConfig.getBandWindow())
        .upperRate(strategyConfig.getUpperRate())
        .lowerRate(strategyConfig.getLowerRate())
        .purchaseFxRate(request.getPurchaseFxRate())
        .currentFxRate(request.getCurrentFxRate())
        // 밴드 아래에서 전량 팔아 재투자 끔
        .reinvestDividends(false)
        .dividendTaxRate(orZero(request.getDividendTaxRate()))
        .tradingFeeRate(orZero(request.getTradingFeeRate()))
        .capitalGainsTax(request.getCapitalGainsTax())
        .periodEnd(request.getPeriodEnd())
        .build();

      StrategyResponse result = execute(maBandRequest);
      return responseMapper.toComparisonItemFromStrategy(result, strategyName);
    }

    if (strategyType == StrategyType.SIMPLE) {
      SimulationRequest simulationRequest = simulationWithOptions(request)
        .symbol(request.getSymbol())
        .purchaseDate(strategyConfig.getPurchaseDate())
        .build();

      SimulationResponse result = tradingSimulationService.runSimulation(simulationRequest,
        false);
      return responseMapper.toComparisonItemFromSimulation(result, strategyName);
    }

    throw new BacktestException(BacktestResponse.STRATEGY_TYPE_MISMATCH, "비교할 수 없는 전략 타입: " + strategyType);
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
    backtestHistoryUtils.saveBacktestHistory(request.getUserId(), BacktestType.COMPARISON, request);
  }

}
