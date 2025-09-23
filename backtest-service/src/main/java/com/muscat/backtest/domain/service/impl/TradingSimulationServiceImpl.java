package com.muscat.backtest.domain.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.enums.type.BacktestType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.logging.BacktestLogger;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.BacktestDataUtils;
import com.muscat.backtest.common.util.BacktestHistoryUtils;
import com.muscat.backtest.domain.dto.request.InvestmentRequest;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.response.InvestmentResponse;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.entity.InvestmentBacktestResult;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.repository.InvestmentBacktestResultRepository;
import com.muscat.backtest.domain.service.TradingSimulationService;
import com.muscat.backtest.infra.client.MarketDataClient;
import com.muscat.backtest.infra.client.TradeServiceClient;
import com.muscat.backtest.infra.client.dto.DividendHistoryDto;
import com.muscat.backtest.infra.client.dto.HoldingDto;
import com.muscat.backtest.infra.client.dto.OHLCPriceDto;
import com.muscat.backtest.infra.client.dto.StockPriceDto;
import com.muscat.backtest.infra.client.dto.TradeDto;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingSimulationServiceImpl implements TradingSimulationService {

  private final MarketDataClient marketDataClient;
  private final TradeServiceClient tradeServiceClient;
  private final ResponseMapper responseMapper;
  private final BacktestHistoryUtils backtestHistoryUtils;
  private final InvestmentBacktestResultRepository investmentBacktestResultRepository;
  private final ObjectMapper objectMapper;

  // 과거 특정 시점 투자 시뮬레이션을 실행하고 결과를 반환
  @Override
  public SimulationResponse runSimulation(SimulationRequest request) {
    return runSimulation(request, true);
  }

  @Override
  public SimulationResponse runSimulation(SimulationRequest request, boolean recordHistory) {
    BacktestLogger.setBacktestContext(request.getUserId(), "SIMULATION", request.getSymbol());
    log.info("백테스팅 시뮬레이션 시작: {}", request);

    try {
      SimulationContext context = prepareSimulationContext(request);
      SimulationCalculationResult calculation = calculateSimulation(context);
      SimulationResponse response = buildSimulationResponse(context, calculation);

      if (recordHistory) {
        recordSimulationHistory(request);
      }

      return response;
    } finally {
      BacktestLogger.remove("operation");
    }
  }

  // Trade에서 과거에 매수한 주식의 현재까지 백테스트 결과 계산
  @Override
  public InvestmentResponse executeInvestment(InvestmentRequest request, String authorization) {
    log.info("과거 매수 백테스트 시작: {}", request);

    try {
      // 사용자의 모든 portfolio 조회
      List<HoldingDto> holdings = tradeServiceClient.getPortfolio(authorization);

      if (holdings.isEmpty()) {
        throw new BacktestException(BacktestResponse.HOLDING_DATA_NOT_FOUND,
            "해당 조건에 맞는 보유 주식을 찾을 수 없습니다");
      }

      List<InvestmentResponse> results = holdings.stream()
          .map(holding -> createInvestmentBacktest(authorization, holding))
          .toList();

      InvestmentResponse finalResult = resolvePortfolioResult(results);
      saveInvestmentBacktestResult(request.getUserId(), finalResult);

      // 백테스트 히스토리 기록
      backtestHistoryUtils.saveBacktestHistory(request.getUserId(),
          BacktestType.INVESTMENT_ANALYSIS, request);

      return finalResult;
    } catch (BacktestException e) {
      throw e;
    } catch (Exception e) {
      log.error("과거 매수 백테스트 중 예상치 못한 오류: {}", e.getMessage(), e);

      if (e.getMessage().contains("환율") || e.getMessage().contains("FX")) {
        throw new BacktestException(BacktestResponse.FX_CONVERSION_ERROR,
            "환율 변환 중 오류: " + e.getMessage());
      } else if (e.getMessage().contains("배당") || e.getMessage().contains("dividend")) {
        throw new BacktestException(BacktestResponse.DIVIDEND_CALCULATION_ERROR,
            "배당금 계산 중 오류: " + e.getMessage());
      } else if (e.getMessage().contains("수익률") || e.getMessage().contains("return")) {
        throw new BacktestException(BacktestResponse.RETURN_CALCULATION_ERROR,
            "수익률 계산 중 오류: " + e.getMessage());
      } else {
        throw new BacktestException(BacktestResponse.CALCULATION_ERROR,
            "백테스트 계산 중 오류: " + e.getMessage());
      }
    }
  }


  private InvestmentResponse createInvestmentBacktest(String authorization, HoldingDto holding) {
    LocalDate earliestBuyDate = findEarliestBuyDate(authorization, holding);
    SimulationResponse backtestResult = calculateHoldingPerformance(holding, earliestBuyDate);
    return responseMapper.toInvestmentResponse(holding, backtestResult);
  }

  private LocalDate findEarliestBuyDate(String authorization, HoldingDto holding) {
    List<TradeDto> tradeHistory = tradeServiceClient.getTradeHistoryBySymbol(authorization,
        holding.getSymbol());

    return tradeHistory.stream()
        .filter(trade -> "BUY".equals(trade.getTradeType()))
        .map(TradeDto::getTradeDate)
        .min(LocalDate::compareTo)
        .orElse(holding.getPurchaseDate());
  }

  private InvestmentResponse resolvePortfolioResult(List<InvestmentResponse> results) {
    if (results.isEmpty()) {
      throw new BacktestException(BacktestResponse.HOLDING_DATA_NOT_FOUND,
          "보유 주식 백테스트 결과가 존재하지 않습니다");
    }

    if (results.size() > 1) {
      log.info("다중 보유 종목 백테스트 결과 {}건 - 통합 응답 TODO (임시 첫 번째 결과 반환)",
          results.size());
    }

    return results.get(0);
  }


  private void saveInvestmentBacktestResult(String userId, InvestmentResponse result) {
    executeWithFallback(() -> {
      try {
        String resultJson = objectMapper.writeValueAsString(result);
        LocalDateTime now = LocalDateTime.now();

        InvestmentBacktestResult entity = investmentBacktestResultRepository
            .findByUserId(userId)
            .map(existing -> existing.updateResult(resultJson, now))
            .orElse(InvestmentBacktestResult.builder()
                .userId(userId)
                .resultData(resultJson)
                .calculatedAt(now)
                .build());

        investmentBacktestResultRepository.save(entity);
        log.info("투자 백테스트 결과 저장 완료: userId={}, calculatedAt={}", userId, now);
        return null;
      } catch (JsonProcessingException e) {
        log.warn("JSON 변환 오류: userId={}, error={}", userId, e.getMessage());
        return null;
      }
    }, "투자 백테스트 결과 저장", userId);
  }

  @Override
  public Optional<InvestmentResponse> getCachedInvestmentResult(String userId) {
    Optional<Optional<InvestmentResponse>> result = executeWithFallback(() -> {
      Optional<InvestmentBacktestResult> cachedResult = getValidCachedEntity(userId);
      if (cachedResult.isEmpty()) {
        return Optional.empty();
      }

      try {
        InvestmentResponse response = objectMapper.readValue(
            cachedResult.get().getResultData(), InvestmentResponse.class);
        log.debug("캐시된 투자 백테스트 결과 조회 성공: userId={}", userId);
        return Optional.of(response);
      } catch (JsonProcessingException e) {
        log.warn("JSON 파싱 오류: userId={}, error={}", userId, e.getMessage());
        return Optional.empty();
      }
    }, "캐시된 투자 백테스트 결과 조회", userId);

    return result.orElse(Optional.empty());
  }

  @Override
  public Optional<InvestmentBacktestResult> getCachedInvestmentResultEntity(String userId) {
    return executeWithFallback(() -> getValidCachedEntity(userId),
        "캐시된 투자 백테스트 Entity 조회", userId).orElse(Optional.empty());
  }

  private SimulationContext prepareSimulationContext(SimulationRequest request) {
    OHLCPriceDto purchaseData = BacktestDataUtils.getHistoricalPrice(
        marketDataClient, request.getSymbol(), request.getPurchaseDate());
    MarketDataClient.FxRate purchaseFxRate = BacktestDataUtils.getHistoricalFxRate(
        marketDataClient, request.getPurchaseDate());
    StockPriceDto currentPrice = BacktestDataUtils.getCurrentPrice(
        marketDataClient, request.getSymbol());
    MarketDataClient.FxRate currentFxRate = BacktestDataUtils.getCurrentFxRate(marketDataClient);
    DividendHistoryDto dividendHistory = BacktestDataUtils.getDividendHistory(
        marketDataClient, request.getSymbol(), request.getPurchaseDate(), LocalDate.now());

    return new SimulationContext(request, purchaseData, purchaseFxRate, currentPrice, currentFxRate,
        dividendHistory);
  }

  private SimulationCalculationResult calculateSimulation(SimulationContext context) {
    BigDecimal purchasePriceUsd = context.purchaseData().getClosePrice();
    BigDecimal purchaseFxRate = context.purchaseFxRate().rate();
    BigDecimal currentFxRate = context.currentFxRate().rate();
    BigDecimal currentPriceUsd = context.currentPrice().getCurrentPrice();

    BigDecimal usdAmount = MoneyUtils.calculateKrwToUsd(
        context.request().getInvestmentAmount(), purchaseFxRate);
    log.debug("환전 계산: KRW {} → USD {} (환율: {})",
        context.request().getInvestmentAmount(), usdAmount, purchaseFxRate);

    BigDecimal shares = BacktestCalculationUtils.calculateWholeSharesWithFee(
        usdAmount, purchasePriceUsd, context.request().getTradingFeeRate());
    BigDecimal tradingFee = BacktestCalculationUtils.calculateTradingFee(
        usdAmount, context.request().getTradingFeeRate());
    BigDecimal totalCost = BacktestCalculationUtils.calculateTotalCost(
        shares, purchasePriceUsd, tradingFee);
    BigDecimal remainingCash = BacktestCalculationUtils.calculateRemainingCash(usdAmount, totalCost);

    BigDecimal currentValueUsd = shares.multiply(currentPriceUsd);
    BigDecimal currentValueKrw = MoneyUtils.calculateUsdToKrw(currentValueUsd, currentFxRate);

    BigDecimal stockReturn = currentPriceUsd.subtract(purchasePriceUsd);
    BigDecimal stockReturnPercent = MoneyUtils.calculateReturnRate(purchasePriceUsd, currentPriceUsd);
    BigDecimal fxReturn = currentFxRate.subtract(purchaseFxRate);
    BigDecimal fxReturnPercent = MoneyUtils.calculateReturnRate(purchaseFxRate, currentFxRate);

    BigDecimal totalDividends = BacktestCalculationUtils.calculateTotalDividends(
        context.dividendHistory(), shares, context.request().getPurchaseDate(), LocalDate.now());
    BigDecimal dividendYield = BacktestCalculationUtils.calculateDividendYield(
        totalDividends, shares, currentPriceUsd);

    BigDecimal totalDividendsKrw = totalDividends.compareTo(BigDecimal.ZERO) > 0
        ? MoneyUtils.calculateUsdToKrw(totalDividends, currentFxRate)
        : BigDecimal.ZERO;

    BigDecimal totalReturnKrw = currentValueKrw.subtract(context.request().getInvestmentAmount())
        .add(totalDividendsKrw);
    BigDecimal totalReturnPercent = MoneyUtils.calculateReturnRate(
        context.request().getInvestmentAmount(), currentValueKrw.add(totalDividendsKrw));

    return new SimulationCalculationResult(
        purchasePriceUsd, shares, currentPriceUsd, currentValueUsd, currentValueKrw,
        stockReturn, stockReturnPercent, purchaseFxRate, currentFxRate, fxReturn, fxReturnPercent,
        totalDividends, dividendYield, tradingFee, remainingCash, totalReturnKrw, totalReturnPercent);
  }

  private SimulationResponse buildSimulationResponse(SimulationContext context,
      SimulationCalculationResult calculation) {
    return responseMapper.toSimulationResponse(
        context.request(),
        calculation.purchasePriceUsd(),
        calculation.shares(),
        calculation.currentPriceUsd(),
        calculation.currentValueUsd(),
        calculation.currentValueKrw(),
        calculation.stockReturn(),
        calculation.stockReturnPercent(),
        calculation.purchaseFxRate(),
        calculation.currentFxRate(),
        calculation.fxReturn(),
        calculation.fxReturnPercent(),
        calculation.totalDividends(),
        calculation.dividendYield(),
        calculation.tradingFee(),
        calculation.remainingCash(),
        calculation.totalReturnKrw(),
        calculation.totalReturnPercent());
  }

  private void recordSimulationHistory(SimulationRequest request) {
    if (request.getUserId() == null) {
      return;
    }
    backtestHistoryUtils.saveBacktestHistory(request.getUserId(), BacktestType.COMPARISON, request);
  }

  private Optional<InvestmentBacktestResult> getValidCachedEntity(String userId) {
    return investmentBacktestResultRepository.findByUserId(userId)
        .filter(entity -> entity.getCalculatedAt() != null);
  }

  private <T> Optional<T> executeWithFallback(Supplier<T> operation, String operationName,
      String userId) {
    try {
      return Optional.ofNullable(operation.get());
    } catch (Exception e) {
      log.warn("{} 실패: userId={}, error={}", operationName, userId, e.getMessage());
    }
    return Optional.empty();
  }

  // Holdings 기반으로 현재까지의 수익률 계산 (환전 검증 없이)
  private SimulationResponse calculateHoldingPerformance(HoldingDto holding,
      LocalDate purchaseDate) {
    try {
      log.debug("Holdings 데이터: symbol={}, shares={}, avgPrice={}",
          holding.getSymbol(), holding.getShares(), holding.getAveragePrice());
      // 현재 주가 조회
      StockPriceDto currentPrice = marketDataClient.getCurrentPrice(holding.getSymbol());

      // 매수 시점의 환율 조회 (fallback 포함)
      MarketDataClient.FxRate purchaseFxRate = getFxRateWithFallback(purchaseDate);

      // 현재 환율 조회 (fallback 포함)
      MarketDataClient.FxRate currentFxRate = getCurrentFxRateWithFallback();

      // 보유 주식의 현재 가치 계산 (USD)
      BigDecimal currentValueUsd = holding.getShares().multiply(currentPrice.getCurrentPrice())
          .setScale(2, MoneyUtils.ROUND_MODE);

      // 현재 가치 (KRW)
      BigDecimal currentValueKrw = currentValueUsd.multiply(currentFxRate.rate())
          .setScale(0, MoneyUtils.ROUND_MODE);

      // 주식 수익 계산 (USD)
      BigDecimal purchaseValueUsd = holding.getShares().multiply(holding.getAveragePrice());
      BigDecimal stockReturn = currentValueUsd.subtract(purchaseValueUsd);

      log.debug("수익률 계산: 매수가치=${}, 현재가치=${}", purchaseValueUsd, currentValueUsd);

      // 수익률 계산 (0으로 나누기 방지)
      BigDecimal stockReturnPercent = calculateReturnRateWithZeroCheck(
          purchaseValueUsd, currentValueUsd, "매수 가치");

      // 환차익 계산
      BigDecimal fxReturnPercent = MoneyUtils.calculateReturnRate(purchaseFxRate.rate(),
          currentFxRate.rate());
      BigDecimal fxReturn = currentFxRate.rate().subtract(purchaseFxRate.rate());

      // 총 수익 계산 (KRW 기준)
      BigDecimal totalReturnKrw = currentValueKrw.subtract(holding.getTotalInvested());
      BigDecimal totalReturnPercent = calculateReturnRateWithZeroCheck(
          holding.getTotalInvested(), currentValueKrw, "총 투자금액");

      return createHoldingSimulationResponse(holding, purchaseDate, currentPrice, currentValueUsd,
          currentValueKrw, stockReturn, stockReturnPercent, purchaseFxRate, currentFxRate,
          fxReturn, fxReturnPercent, totalReturnKrw, totalReturnPercent);

    } catch (Exception e) {
      log.error("Holdings 성과 계산 중 오류: {}", e.getMessage(), e);
      throw new BacktestException(BacktestResponse.CALCULATION_ERROR,
          "Holdings 성과 계산 중 오류: " + e.getMessage());
    }
  }

  // 환율 조회 with fallback logic
  private MarketDataClient.FxRate getFxRateWithFallback(LocalDate date) {
    // 1차 시도: 해당 날짜의 환율 조회
    try {
      MarketDataClient.FxRate rate = marketDataClient.getFxRate(date.toString());
      log.debug("{}일자 환율 조회 성공: {}", date, rate.rate());
      return rate;
    } catch (Exception e) {
      log.warn("{}일자 환율 데이터 없음: {}", date, e.getMessage());
    }

    // 2차 시도: 최신 환율 조회
    try {
      MarketDataClient.FxRate latestRate = marketDataClient.getLatestFxRate();
      log.info("{}일자 환율 대신 최신 환율 사용: {}", date, latestRate.rate());

      // 요청한 날짜로 FxRate 생성 (rate는 최신 것 사용)
      return new MarketDataClient.FxRate(date, latestRate.rate());
    } catch (Exception fallbackError) {
      log.warn("최신 환율 조회도 실패: {}", fallbackError.getMessage());
    }

    // 3차 시도: 기본 환율 사용 (1,300원)
    log.warn("모든 환율 조회 실패, 기본 환율 1,300원 사용 ({}일자)", date);
    return new MarketDataClient.FxRate(date, new BigDecimal("1300.00"));
  }

  // 현재 환율 조회 with fallback logic
  private MarketDataClient.FxRate getCurrentFxRateWithFallback() {
    try {
      MarketDataClient.FxRate latestRate = marketDataClient.getLatestFxRate();
      log.debug("최신 환율 조회 성공: {}", latestRate.rate());
      return latestRate;
    } catch (Exception e) {
      log.warn("최신 환율 조회 실패, 기본 환율 1,300원 사용: {}", e.getMessage());
      return new MarketDataClient.FxRate(LocalDate.now(), new BigDecimal("1300.00"));
    }
  }

  // Holdings 기반 SimulationResponse 생성
  private SimulationResponse createHoldingSimulationResponse(HoldingDto holding,
      LocalDate purchaseDate,
      StockPriceDto currentPrice, BigDecimal currentValueUsd, BigDecimal currentValueKrw,
      BigDecimal stockReturn, BigDecimal stockReturnPercent, MarketDataClient.FxRate purchaseFxRate,
      MarketDataClient.FxRate currentFxRate, BigDecimal fxReturn, BigDecimal fxReturnPercent,
      BigDecimal totalReturnKrw, BigDecimal totalReturnPercent) {

    return SimulationResponse.builder()
        .symbol(holding.getSymbol())
        .purchaseDate(purchaseDate)
        .currentDate(LocalDate.now())
        .investmentAmount(holding.getTotalInvested())
        .purchasePrice(holding.getAveragePrice())
        .shares(holding.getShares().setScale(6, MoneyUtils.ROUND_MODE))
        .currentPrice(currentPrice.getCurrentPrice())
        .currentValue(MoneyUtils.roundUsd(currentValueUsd))
        .stockReturn(MoneyUtils.roundUsd(stockReturn))
        .stockReturnPercent(MoneyUtils.roundUsd(stockReturnPercent))
        .purchaseFxRate(purchaseFxRate.rate())
        .currentFxRate(currentFxRate.rate())
        .fxReturn(MoneyUtils.roundUsd(fxReturn))
        .fxReturnPercent(MoneyUtils.roundUsd(fxReturnPercent))
        .totalDividends(BigDecimal.ZERO)
        .dividendYield(BigDecimal.ZERO)
        .tradingFee(BigDecimal.ZERO)
        .remainingCash(BigDecimal.ZERO)
        .totalReturn(MoneyUtils.roundUsd(totalReturnKrw))
        .totalReturnPercent(MoneyUtils.roundUsd(totalReturnPercent))
        .currentValueKrw(MoneyUtils.roundKrw(currentValueKrw))
        .totalReturnKrw(MoneyUtils.roundKrw(totalReturnKrw))
        .remainingCashKrw(BigDecimal.ZERO)
        .performanceSummary(
            createPerformanceSummary(stockReturn, totalReturnPercent, fxReturnPercent))
        .build();
  }

  // 성과 요약 문자열 생성
  private String createPerformanceSummary(BigDecimal stockReturn, BigDecimal totalReturnPercent,
      BigDecimal fxReturnPercent) {
    return String.format("총 수익: $%.2f (%.2f%%), 환차익: %.2f%%",
        stockReturn, totalReturnPercent, fxReturnPercent);
  }

  // 0으로 나누기 방지하며 수익률 계산
  private BigDecimal calculateReturnRateWithZeroCheck(BigDecimal baseValue, BigDecimal currentValue,
      String valueName) {
    if (baseValue.compareTo(BigDecimal.ZERO) == 0) {
      log.warn("{}이(가) 0입니다. 수익률을 0으로 설정", valueName);
      return BigDecimal.ZERO;
    }
    return MoneyUtils.calculateReturnRate(baseValue, currentValue);
  }

  private record SimulationContext(
      SimulationRequest request,
      OHLCPriceDto purchaseData,
      MarketDataClient.FxRate purchaseFxRate,
      StockPriceDto currentPrice,
      MarketDataClient.FxRate currentFxRate,
      DividendHistoryDto dividendHistory) {}

  private record SimulationCalculationResult(
      BigDecimal purchasePriceUsd,
      BigDecimal shares,
      BigDecimal currentPriceUsd,
      BigDecimal currentValueUsd,
      BigDecimal currentValueKrw,
      BigDecimal stockReturn,
      BigDecimal stockReturnPercent,
      BigDecimal purchaseFxRate,
      BigDecimal currentFxRate,
      BigDecimal fxReturn,
      BigDecimal fxReturnPercent,
      BigDecimal totalDividends,
      BigDecimal dividendYield,
      BigDecimal tradingFee,
      BigDecimal remainingCash,
      BigDecimal totalReturnKrw,
      BigDecimal totalReturnPercent) {}
}
