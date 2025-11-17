package com.muscat.backtest.domain.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscat.backtest.common.constants.BacktestConstants;
import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.enums.type.BacktestType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.logging.BacktestLogger;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
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
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import com.muscat.backtest.infra.client.TradeServiceClientWrapper;
import com.muscat.backtest.infra.client.dto.DividendDto;
import com.muscat.backtest.infra.client.dto.DividendHistoryDto;
import com.muscat.backtest.infra.client.dto.HoldingDto;
import com.muscat.backtest.infra.client.dto.TradeDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.dto.StockPriceDto;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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

  private final MarketDataClientWrapper marketDataClientWrapper;
  private final TradeServiceClientWrapper tradeServiceClientWrapper;
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
      List<HoldingDto> holdings = tradeServiceClientWrapper.getPortfolio(authorization);

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
    List<TradeDto> tradeHistory = tradeServiceClientWrapper.getTradeHistoryBySymbol(authorization,
      holding.symbol());

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

    if (results.size() == 1) {
      return results.get(0);
    }

    // 다중 종목 통합: 포트폴리오 전체 성과 계산
    log.info("다중 보유 종목 백테스트 결과 {}건 - 포트폴리오 통합 응답 생성", results.size());
    return mergePortfolioResults(results);
  }

  /**
   * 여러 종목의 백테스트 결과를 포트폴리오 단위로 통합
   */
  private InvestmentResponse mergePortfolioResults(List<InvestmentResponse> results) {
    BigDecimal totalInvestment = BigDecimal.ZERO;
    BigDecimal totalCurrentValueUsd = BigDecimal.ZERO;
    BigDecimal totalCurrentValueKrw = BigDecimal.ZERO;
    BigDecimal totalDividends = BigDecimal.ZERO;
    BigDecimal totalFees = BigDecimal.ZERO;
    BigDecimal totalRemainingCash = BigDecimal.ZERO;
    BigDecimal totalRemainingCashKrw = BigDecimal.ZERO;

    LocalDate earliestPurchaseDate = null;
    LocalDate latestCurrentDate = null;
    BigDecimal avgCurrentFxRate = BigDecimal.ZERO;

    StringBuilder symbolList = new StringBuilder();
    int successCount = 0;

    for (InvestmentResponse result : results) {
      SimulationResponse sim = result.getSimulation();

      // 투자 금액 및 현재 가치 합산
      totalInvestment = totalInvestment.add(sim.getInvestmentAmount());
      totalCurrentValueUsd = totalCurrentValueUsd.add(sim.getCurrentValue());
      totalCurrentValueKrw = totalCurrentValueKrw.add(sim.getCurrentValueKrw());
      totalDividends = totalDividends.add(sim.getTotalDividends());
      totalFees = totalFees.add(sim.getTradingFee());
      totalRemainingCash = totalRemainingCash.add(sim.getRemainingCash());
      totalRemainingCashKrw = totalRemainingCashKrw.add(sim.getRemainingCashKrw());

      // 날짜 범위 계산
      if (earliestPurchaseDate == null || sim.getPurchaseDate().isBefore(earliestPurchaseDate)) {
        earliestPurchaseDate = sim.getPurchaseDate();
      }
      if (latestCurrentDate == null || sim.getCurrentDate().isAfter(latestCurrentDate)) {
        latestCurrentDate = sim.getCurrentDate();
        avgCurrentFxRate = sim.getCurrentFxRate(); // 가장 최근 환율 사용
      }

      // 종목 리스트 구성
      if (symbolList.length() > 0) {
        symbolList.append(", ");
      }
      symbolList.append(result.getSymbol());

      if ("SUCCESS".equals(result.getStatus())) {
        successCount++;
      }
    }

    // 총 수익 계산
    BigDecimal totalAssetKrw = totalCurrentValueKrw.add(totalRemainingCashKrw)
        .add(totalDividends.multiply(avgCurrentFxRate));
    BigDecimal totalReturnKrw = totalAssetKrw.subtract(totalInvestment);
    BigDecimal totalReturnPercent = totalInvestment.compareTo(BigDecimal.ZERO) > 0
        ? totalReturnKrw.divide(totalInvestment, 4, BigDecimal.ROUND_HALF_UP)
            .multiply(BacktestConstants.Money.PERCENTAGE_MULTIPLIER)
        : BigDecimal.ZERO;

    // 통합 SimulationResponse 생성
    SimulationResponse mergedSim = SimulationResponse.builder()
        .symbol(symbolList.toString())
        .purchaseDate(earliestPurchaseDate)
        .currentDate(latestCurrentDate)
        .investmentAmount(totalInvestment)
        .currentValue(totalCurrentValueUsd)
        .currentValueKrw(totalCurrentValueKrw)
        .totalDividends(totalDividends)
        .tradingFee(totalFees)
        .remainingCash(totalRemainingCash)
        .remainingCashKrw(totalRemainingCashKrw)
        .totalAssetKrw(totalAssetKrw)
        .totalReturnKrw(totalReturnKrw)
        .totalReturnPercent(totalReturnPercent)
        .currentFxRate(avgCurrentFxRate)
        .performanceSummary(String.format("포트폴리오 %d종목, 총 수익률 %.2f%%",
            results.size(), totalReturnPercent))
        .build();

    // 통합 InvestmentResponse 생성
    return InvestmentResponse.builder()
        .simulation(mergedSim)
        .holdingId("PORTFOLIO-" + results.size())
        .symbol(symbolList.toString())
        .purchaseDate(earliestPurchaseDate)
        .investmentAmount(totalInvestment)
        .status(successCount == results.size() ? "SUCCESS" : "PARTIAL_SUCCESS")
        .message(String.format("%d개 종목 중 %d개 성공", results.size(), successCount))
        .portfolioCreated(true)
        .portfolioStatus("ACTIVE")
        .build();
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
    // 주가 데이터 조회 - Wrapper 직접 호출로 resilience 패턴 적용
    OHLCPriceDto purchaseData = getHistoricalPriceWithRetry(request.getSymbol(),
      request.getPurchaseDate());

    // 수동 환율이 설정되어 있으면 사용, 없으면 자동 조회
    MarketDataClient.FxRate purchaseFxRate;
    if (request.getPurchaseFxRate() != null) {
      purchaseFxRate = new MarketDataClient.FxRate(request.getPurchaseDate(),
        request.getPurchaseFxRate());
    } else {
      purchaseFxRate = marketDataClientWrapper.getFxRate(request.getPurchaseDate().toString());
    }

    StockPriceDto currentPrice = marketDataClientWrapper.getCurrentPrice(request.getSymbol());

    MarketDataClient.FxRate currentFxRate;
    if (request.getCurrentFxRate() != null) {
      currentFxRate = new MarketDataClient.FxRate(LocalDate.now(), request.getCurrentFxRate());
    } else {
      currentFxRate = marketDataClientWrapper.getLatestFxRate();
    }

    // 배당 이력 조회
    List<DividendDto> dividendList = marketDataClientWrapper.getDividendHistory(
      request.getSymbol(), request.getPurchaseDate().toString(), LocalDate.now().toString());
    DividendHistoryDto dividendHistory = convertToDividendHistory(request.getSymbol(),
      dividendList);

    return new SimulationContext(request, purchaseData, purchaseFxRate, currentPrice, currentFxRate,
      dividendHistory);
  }

  private SimulationCalculationResult calculateSimulation(SimulationContext context) {
    // adjustedClose 사용 (액면분할/배당 반영), 없으면 closePrice fallback
    BigDecimal purchasePriceUsd = context.purchaseData().adjustedClose() != null
        ? context.purchaseData().adjustedClose()
        : context.purchaseData().closePrice();
    BigDecimal purchaseFxRate = context.purchaseFxRate().rate();
    BigDecimal currentFxRate = context.currentFxRate().rate();
    BigDecimal currentPriceUsd = context.currentPrice().currentPrice();

    BigDecimal usdAmount = MoneyUtils.convertKrwToUsd(
      context.request().getInvestmentAmount(), purchaseFxRate);
    log.debug("환전 계산: KRW {} → USD {} (환율: {})",
      context.request().getInvestmentAmount(), usdAmount, purchaseFxRate);

    BigDecimal shares = BacktestCalculationUtils.calculateSharesWithFee(
      usdAmount, purchasePriceUsd, context.request().getTradingFeeRate());
    BigDecimal tradingFee = BacktestCalculationUtils.calculateTradingFee(
      usdAmount, context.request().getTradingFeeRate());
    BigDecimal totalCost = BacktestCalculationUtils.calculateTotalCost(
      shares, purchasePriceUsd, tradingFee);
    BigDecimal remainingCash = BacktestCalculationUtils.calculateRemainingCash(usdAmount,
      totalCost);

    BigDecimal currentValueUsd = shares.multiply(currentPriceUsd);
    BigDecimal currentValueKrw = MoneyUtils.convertUsdToKrw(currentValueUsd, currentFxRate);

    BigDecimal stockReturn = currentPriceUsd.subtract(purchasePriceUsd);
    BigDecimal stockReturnPercent = MoneyUtils.calculateReturnRate(purchasePriceUsd,
      currentPriceUsd);
    BigDecimal fxReturn = currentFxRate.subtract(purchaseFxRate);
    BigDecimal fxReturnPercent = MoneyUtils.calculateReturnRate(purchaseFxRate, currentFxRate);

    // 재투자된 배당금 추적
    final BigDecimal[] sharesArray = {shares};
    final BigDecimal[] dividendsReinvestedArray = {BigDecimal.ZERO};
    final List<LocalDate> dividendReinvestDates = new ArrayList<>(); // 배당 재투자 날짜 목록

    // 배당금 재투자 처리 - 각 배당일에 순차적으로 재투자
    if (Boolean.TRUE.equals(context.request().getReinvestDividends()) &&
      context.dividendHistory() != null &&
      context.dividendHistory().getDividends() != null &&
      !context.dividendHistory().getDividends().isEmpty()) {

      log.info("배당금 재투자 실행 시작: {} 개의 배당 내역", context.dividendHistory().getDividends().size());

      // 배당 내역을 날짜순으로 정렬하여 순차 재투자
      LocalDate purchaseDate = context.request().getPurchaseDate();

      context.dividendHistory().getDividends().stream()
        .filter(dividend -> dividend.getExDate() != null)
        .filter(dividend -> !dividend.getExDate().isBefore(purchaseDate) &&
          !dividend.getExDate().isAfter(LocalDate.now()))
        .sorted((d1, d2) -> d1.getExDate().compareTo(d2.getExDate()))
        .forEach(dividend -> {
          // 이 배당금 계산 (현재 보유 주식수 기준)
          BigDecimal dividendAmount = dividend.getAmount().multiply(sharesArray[0])
            .setScale(BacktestConstants.Money.SCALE, BacktestConstants.Money.ROUNDING_MODE);

          if (dividendAmount.compareTo(BigDecimal.ZERO) > 0) {
            // 배당 원천징수 적용 (설정된 경우)
            BigDecimal taxRate = context.request().getDividendTaxRate();
            BigDecimal afterTaxDividend = dividendAmount;
            if (taxRate != null && taxRate.compareTo(BigDecimal.ZERO) > 0) {
              BigDecimal taxAmount = dividendAmount.multiply(taxRate)
                .setScale(BacktestConstants.Money.SCALE, BacktestConstants.Money.ROUNDING_MODE);
              afterTaxDividend = dividendAmount.subtract(taxAmount);
              log.info("배당 원천징수: {} - 배당금 ${} → 세후 ${} (세율 {}%)",
                dividend.getExDate(), dividendAmount, afterTaxDividend,
                taxRate.multiply(BacktestConstants.Money.PERCENTAGE_MULTIPLIER));
            }

            // 배당금 지급일의 주가 조회
            var priceAtDividendDate = getHistoricalPriceWithRetry(
              context.request().getSymbol(), dividend.getExDate());

            // adjustedClose 사용 (액면분할/배당 반영), 없으면 closePrice fallback
            BigDecimal dividendDayPrice = priceAtDividendDate.adjustedClose() != null
                ? priceAtDividendDate.adjustedClose()
                : priceAtDividendDate.closePrice();

            // 세후 배당금으로 매수 가능한 주식수
            BigDecimal additionalShares = afterTaxDividend.divide(
              dividendDayPrice, 8, java.math.RoundingMode.HALF_UP);

            sharesArray[0] = sharesArray[0].add(additionalShares);
            dividendsReinvestedArray[0] = dividendsReinvestedArray[0].add(afterTaxDividend);
            dividendReinvestDates.add(dividend.getExDate()); // 배당 날짜 목록에 추가

            log.info("배당 재투자: {} - ${} ({}주 보유) -> {}주 추가 매수 @${}",
              dividend.getExDate(), afterTaxDividend, sharesArray[0],
              additionalShares, dividendDayPrice);
          }
        });

      log.info("배당금 재투자 완료: 총 ${} 재투자, 최종 보유 {}주", dividendsReinvestedArray[0], sharesArray[0]);
    }

    shares = sharesArray[0];
    BigDecimal dividendsReinvested = dividendsReinvestedArray[0];

    // 재투자 후 현재 가치 재계산
    currentValueUsd = shares.multiply(currentPriceUsd);
    currentValueKrw = MoneyUtils.convertUsdToKrw(currentValueUsd, currentFxRate);

    // 배당금 계산 (표시용 - 재투자 여부와 관계없이 총 배당금 계산)
    BigDecimal totalDividends = BacktestCalculationUtils.calculateTotalDividends(
      context.dividendHistory(), shares, context.request().getPurchaseDate(), LocalDate.now());

    BigDecimal dividendYield = BacktestCalculationUtils.calculateDividendYield(
      totalDividends, shares, currentPriceUsd);

    BigDecimal totalDividendsKrw = totalDividends.compareTo(BigDecimal.ZERO) > 0
      ? MoneyUtils.convertUsdToKrw(totalDividends, currentFxRate)
      : BigDecimal.ZERO;

    // 남은 현금도 자산에 포함 (USD → KRW 환산)
    BigDecimal remainingCashKrw = remainingCash.compareTo(BigDecimal.ZERO) > 0
      ? MoneyUtils.convertUsdToKrw(remainingCash, currentFxRate)
      : BigDecimal.ZERO;

    // 재투자된 배당금이 있으면, 이미 주식 가치에 포함되어 있으므로 배당금을 중복 더하지 않음
    boolean hasReinvested = dividendsReinvested.compareTo(BigDecimal.ZERO) > 0;
    BigDecimal dividendsToAdd = hasReinvested ? BigDecimal.ZERO : totalDividendsKrw;

    // 총 자산 = 주식 가치 + 남은 현금 + 배당금 (재투자 시 배당금 제외)
    BigDecimal totalAssetKrw = currentValueKrw.add(remainingCashKrw).add(dividendsToAdd);

    // 총 수익 = 총 자산 - 투자금
    BigDecimal totalReturnKrw = totalAssetKrw.subtract(context.request().getInvestmentAmount());

    // 총 수익률 = (총 자산 / 투자금) - 1
    BigDecimal totalReturnPercent = MoneyUtils.calculateReturnRate(
      context.request().getInvestmentAmount(), totalAssetKrw);

    return new SimulationCalculationResult(
      purchasePriceUsd, shares, currentPriceUsd, currentValueUsd, currentValueKrw,
      stockReturn, stockReturnPercent, purchaseFxRate, currentFxRate, fxReturn, fxReturnPercent,
      totalDividends, dividendYield, tradingFee, remainingCash, totalAssetKrw, totalReturnKrw,
      totalReturnPercent,
      dividendsReinvested, dividendReinvestDates);
  }

  private SimulationResponse buildSimulationResponse(SimulationContext context,
    SimulationCalculationResult calculation) {

    // 최적 타이밍 계산 (매수일 ~ 현재)
    OptimalTiming optimalTiming = calculateOptimalTiming(
      context.request().getSymbol(),
      context.request().getPurchaseDate(),
      LocalDate.now());

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
      calculation.totalAssetKrw(),
      calculation.totalReturnKrw(),
      calculation.totalReturnPercent(),
      optimalTiming.buyDate(),
      optimalTiming.buyPrice(),
      optimalTiming.sellDate(),
      optimalTiming.sellPrice(),
      optimalTiming.returnPercent(),
      calculation.dividendsReinvested(),
      calculation.dividendReinvestDates());
  }

  // 최적 타이밍 계산 (기간 내 최저가 매수, 최고가 매도)
  private OptimalTiming calculateOptimalTiming(String symbol, LocalDate startDate,
    LocalDate endDate) {
    try {
      List<OHLCPriceDto> prices = marketDataClientWrapper.getOHLCPriceRange(
        symbol, startDate.toString(), endDate.toString());

      if (prices == null || prices.isEmpty()) {
        return OptimalTiming.empty();
      }

      // 최저가 찾기 (최적 매수)
      OHLCPriceDto lowestPrice = prices.stream()
        .min(Comparator.comparing(OHLCPriceDto::closePrice))
        .orElse(null);

      // 최고가 찾기 (최적 매도)
      OHLCPriceDto highestPrice = prices.stream()
        .max(Comparator.comparing(OHLCPriceDto::closePrice))
        .orElse(null);

      if (lowestPrice == null || highestPrice == null) {
        return OptimalTiming.empty();
      }

      // 최적 수익률 계산
      BigDecimal optimalReturn = MoneyUtils.calculateReturnRate(
        lowestPrice.closePrice(), highestPrice.closePrice());

      return new OptimalTiming(
        lowestPrice.date(),
        lowestPrice.closePrice(),
        highestPrice.date(),
        highestPrice.closePrice(),
        optimalReturn
      );
    } catch (Exception e) {
      log.warn("최적 타이밍 계산 실패: {}", e.getMessage());
      return OptimalTiming.empty();
    }
  }

  // 최적 타이밍 정보를 담는 레코드
  private record OptimalTiming(
    LocalDate buyDate,
    BigDecimal buyPrice,
    LocalDate sellDate,
    BigDecimal sellPrice,
    BigDecimal returnPercent
  ) {

    static OptimalTiming empty() {
      return new OptimalTiming(null, null, null, null, null);
    }
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
        holding.symbol(), holding.getShares(), holding.getAveragePrice());
      // 현재 주가 조회
      StockPriceDto currentPrice = marketDataClientWrapper.getCurrentPrice(holding.symbol());

      // 매수 시점의 환율 조회 (fallback 포함)
      MarketDataClient.FxRate purchaseFxRate = getFxRateWithFallback(purchaseDate);

      // 현재 환율 조회 (fallback 포함)
      MarketDataClient.FxRate currentFxRate = getCurrentFxRateWithFallback();

      // 보유 주식의 현재 가치 계산 (USD)
      BigDecimal currentValueUsd = holding.getShares().multiply(currentPrice.currentPrice())
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
      MarketDataClient.FxRate rate = marketDataClientWrapper.getFxRate(date.toString());
      log.debug("{}일자 환율 조회 성공: {}", date, rate.rate());
      return rate;
    } catch (Exception e) {
      log.warn("{}일자 환율 데이터 없음: {}", date, e.getMessage());
    }

    // 2차 시도: 최신 환율 조회
    try {
      MarketDataClient.FxRate latestRate = marketDataClientWrapper.getLatestFxRate();
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
      MarketDataClient.FxRate latestRate = marketDataClientWrapper.getLatestFxRate();
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
      .symbol(holding.symbol())
      .purchaseDate(purchaseDate)
      .currentDate(LocalDate.now())
      .investmentAmount(holding.getTotalInvested())
      .purchasePrice(holding.getAveragePrice())
      .shares(holding.getShares().setScale(6, MoneyUtils.ROUND_MODE))
      .currentPrice(currentPrice.currentPrice())
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

  // 주가 데이터 조회 with retry (시장 휴일 대응)
  private OHLCPriceDto getHistoricalPriceWithRetry(String symbol, LocalDate date) {
    log.info("주가 데이터 요청: symbol={}, date={}", symbol, date);

    // 최대 5일까지 과거로 거슬러 올라가며 데이터 찾기
    LocalDate searchDate = date;

    for (int i = 0; i < 5; i++) {
      try {
        OHLCPriceDto response = marketDataClientWrapper.getOHLCPrice(symbol, searchDate.toString());

        if (response != null && response.available()) {
          if (!searchDate.equals(date)) {
            log.info("시장 휴일로 인한 대체 데이터 사용: 요청일={}, 사용일={}", date, searchDate);
          }
          return response;
        }
      } catch (Exception e) {
        log.debug("주가 데이터 조회 실패: symbol={}, searchDate={}, error={}",
          symbol, searchDate, e.getMessage());
      }

      // 하루 전으로 이동
      searchDate = searchDate.minusDays(1);
    }

    // 5일 동안 데이터를 찾지 못한 경우
    throw new BacktestException(BacktestResponse.STOCK_DATA_NOT_FOUND,
      "주가 데이터를 찾을 수 없습니다 (5일 검색): " + symbol + ", " + date);
  }

  // DividendDto List를 DividendHistoryDto로 변환
  private DividendHistoryDto convertToDividendHistory(String symbol,
    List<DividendDto> dividendList) {
    if (dividendList == null || dividendList.isEmpty()) {
      log.warn("배당 데이터를 찾을 수 없습니다: {}", symbol);
      DividendHistoryDto emptyHistory = new DividendHistoryDto();
      emptyHistory.setSymbol(symbol);
      emptyHistory.setDividends(java.util.Collections.emptyList());
      return emptyHistory;
    }

    log.info("배당 데이터 조회 성공: symbol={}, count={}", symbol, dividendList.size());

    DividendHistoryDto history = new DividendHistoryDto();
    history.setSymbol(symbol);

    // DividendDto를 DividendPayment로 변환
    var payments = dividendList.stream()
      .map(dto -> {
        var payment = new DividendHistoryDto.DividendPayment();
        payment.setExDate(dto.exDate());
        payment.setPayDate(dto.paymentDate());
        payment.setAmount(dto.amount());
        payment.setFrequency(null); // frequency는 API에서 제공하지 않음
        return payment;
      })
      .toList();

    history.setDividends(payments);
    log.info("배당 데이터 변환 완료: symbol={}, dividends={}", symbol, payments.size());

    return history;
  }

  private record SimulationContext(
    SimulationRequest request,
    OHLCPriceDto purchaseData,
    MarketDataClient.FxRate purchaseFxRate,
    StockPriceDto currentPrice,
    MarketDataClient.FxRate currentFxRate,
    DividendHistoryDto dividendHistory) {

  }

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
    BigDecimal totalAssetKrw,  // 추가: 총 자산 (주식 + 현금 + 배당)
    BigDecimal totalReturnKrw,
    BigDecimal totalReturnPercent,
    BigDecimal dividendsReinvested,
    List<LocalDate> dividendReinvestDates) {

  }
}
