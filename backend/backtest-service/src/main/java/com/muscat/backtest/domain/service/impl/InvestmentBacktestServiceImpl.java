package com.muscat.backtest.domain.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscat.backtest.common.constants.BacktestConstants;
import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.enums.type.BacktestType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.util.BacktestHistoryUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.common.util.FxFallback;
import com.muscat.backtest.domain.dto.request.InvestmentRequest;
import com.muscat.backtest.domain.dto.response.InvestmentResponse;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.entity.InvestmentBacktestResult;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.repository.InvestmentBacktestResultRepository;
import com.muscat.backtest.domain.service.InvestmentBacktestService;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import com.muscat.backtest.infra.client.TradeServiceClientWrapper;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.backtest.infra.client.dto.HoldingDto;
import com.muscat.backtest.infra.client.dto.TradeDto;
import com.muscat.commonlib.dto.StockPriceDto;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 보유종목(holdings) 기반 투자 백테스트
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvestmentBacktestServiceImpl implements InvestmentBacktestService {

  private final MarketDataClientWrapper marketDataClientWrapper;
  private final TradeServiceClientWrapper tradeServiceClientWrapper;
  private final ResponseMapper responseMapper;
  private final BacktestHistoryUtils backtestHistoryUtils;
  private final InvestmentBacktestResultRepository investmentBacktestResultRepository;
  private final ObjectMapper objectMapper;

  // Trade에서 과거에 매수한 주식의 현재까지 백테스트 결과 계산
  @Override
  public InvestmentResponse executeInvestment(InvestmentRequest request, String authorization) {
    log.info("과거 매수 백테스트 시작: {}", request);

    try {
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

  // 캐시된 투자 백테스트 결과 조회 (JSON → InvestmentResponse 역직렬화)
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

  // 캐시된 투자 백테스트 Entity 조회 (Trade 서비스 내부 통신용)
  @Override
  public Optional<InvestmentBacktestResult> getCachedInvestmentResultEntity(String userId) {
    return executeWithFallback(() -> getValidCachedEntity(userId),
      "캐시된 투자 백테스트 Entity 조회", userId).orElse(Optional.empty());
  }

  // ====== 헬퍼 메소드 ======

  // 단일 보유종목 백테스트 (최초 매수일 산정 → 성과 계산 → 응답 매핑)
  private InvestmentResponse createInvestmentBacktest(String authorization, HoldingDto holding) {
    LocalDate earliestBuyDate = findEarliestBuyDate(authorization, holding);
    SimulationResponse backtestResult = calculateHoldingPerformance(holding, earliestBuyDate);
    return responseMapper.toInvestmentResponse(holding, backtestResult);
  }

  // 종목 최초 BUY 거래일 (없으면 보유 매수일 fallback)
  private LocalDate findEarliestBuyDate(String authorization, HoldingDto holding) {
    List<TradeDto> tradeHistory = tradeServiceClientWrapper.getTradeHistoryBySymbol(authorization,
      holding.symbol());

    return tradeHistory.stream()
      .filter(trade -> "BUY".equals(trade.getTradeType()))
      .map(TradeDto::getTradeDate)
      .min(LocalDate::compareTo)
      .orElse(holding.getPurchaseDate());
  }

  // 종목 결과 정리 (1건이면 그대로, 다건이면 포트폴리오 통합)
  private InvestmentResponse resolvePortfolioResult(List<InvestmentResponse> results) {
    if (results.isEmpty()) {
      throw new BacktestException(BacktestResponse.HOLDING_DATA_NOT_FOUND,
        "보유 주식 백테스트 결과가 존재하지 않습니다");
    }

    if (results.size() == 1) {
      return results.get(0);
    }

    log.info("다중 보유 종목 백테스트 결과 {}건 - 포트폴리오 통합 응답 생성", results.size());
    return mergePortfolioResults(results);
  }

  // 다종목 결과를 포트폴리오 단위로 합산 통합
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

      totalInvestment = totalInvestment.add(sim.getInvestmentAmount());
      totalCurrentValueUsd = totalCurrentValueUsd.add(sim.getCurrentValue());
      totalCurrentValueKrw = totalCurrentValueKrw.add(sim.getCurrentValueKrw());
      totalDividends = totalDividends.add(sim.getTotalDividends());
      totalFees = totalFees.add(sim.getTradingFee());
      totalRemainingCash = totalRemainingCash.add(sim.getRemainingCash());
      totalRemainingCashKrw = totalRemainingCashKrw.add(sim.getRemainingCashKrw());

      if (earliestPurchaseDate == null || sim.getPurchaseDate().isBefore(earliestPurchaseDate)) {
        earliestPurchaseDate = sim.getPurchaseDate();
      }
      if (latestCurrentDate == null || sim.getCurrentDate().isAfter(latestCurrentDate)) {
        latestCurrentDate = sim.getCurrentDate();
        avgCurrentFxRate = sim.getCurrentFxRate();
      }

      if (symbolList.length() > 0) {
        symbolList.append(", ");
      }
      symbolList.append(result.getSymbol());

      if ("SUCCESS".equals(result.getStatus())) {
        successCount++;
      }
    }

    BigDecimal totalAssetKrw = totalCurrentValueKrw.add(totalRemainingCashKrw)
        .add(totalDividends.multiply(avgCurrentFxRate));
    BigDecimal totalReturnKrw = totalAssetKrw.subtract(totalInvestment);
    BigDecimal totalReturnPercent = Decimals.isPositive(totalInvestment)
        ? totalReturnKrw.divide(totalInvestment, 4, RoundingMode.HALF_UP)
            .multiply(BacktestConstants.Money.PERCENTAGE_MULTIPLIER)
        : BigDecimal.ZERO;

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

  // 결과 JSON 직렬화 후 upsert 저장 (userId 기준)
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

  // userId 캐시 Entity 조회 (calculatedAt 있는 유효본만)
  private Optional<InvestmentBacktestResult> getValidCachedEntity(String userId) {
    return investmentBacktestResultRepository.findByUserId(userId)
      .filter(entity -> entity.getCalculatedAt() != null);
  }

  // 공통 예외 처리 래퍼 (실패 시 로그 + Optional.empty)
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
      StockPriceDto currentPrice = marketDataClientWrapper.getCurrentPrice(holding.symbol());

      FxRateDto purchaseFxRate = getFxRateWithFallback(purchaseDate);
      FxRateDto currentFxRate = getCurrentFxRateWithFallback();

      BigDecimal currentValueUsd = holding.getShares().multiply(currentPrice.currentPrice())
        .setScale(2, MoneyUtils.ROUND_MODE);

      BigDecimal currentValueKrw = currentValueUsd.multiply(currentFxRate.rate())
        .setScale(0, MoneyUtils.ROUND_MODE);

      BigDecimal purchaseValueUsd = holding.getShares().multiply(holding.getAveragePrice());
      BigDecimal stockReturn = currentValueUsd.subtract(purchaseValueUsd);

      log.debug("수익률 계산: 매수가치=${}, 현재가치=${}", purchaseValueUsd, currentValueUsd);

      BigDecimal stockReturnPercent = calculateReturnRateWithZeroCheck(
        purchaseValueUsd, currentValueUsd, "매수 가치");

      BigDecimal fxReturnPercent = MoneyUtils.calculateReturnRate(purchaseFxRate.rate(),
        currentFxRate.rate());
      BigDecimal fxReturn = currentFxRate.rate().subtract(purchaseFxRate.rate());

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
  private FxRateDto getFxRateWithFallback(LocalDate date) {
    try {
      FxRateDto rate = marketDataClientWrapper.getFxRate(date.toString());
      log.debug("{}일자 환율 조회 성공: {}", date, rate.rate());
      return rate;
    } catch (Exception e) {
      log.warn("{}일자 환율 데이터 없음: {}", date, e.getMessage());
    }

    try {
      FxRateDto latestRate = marketDataClientWrapper.getLatestFxRate();
      log.info("{}일자 환율 대신 최신 환율 사용: {}", date, latestRate.rate());
      return new FxRateDto(date, latestRate.rate());
    } catch (Exception fallbackError) {
      log.warn("최신 환율 조회도 실패: {}", fallbackError.getMessage());
    }

    log.warn("모든 환율 조회 실패, 기본 환율 {}원 사용 ({}일자)", FxFallback.DEFAULT_RATE, date);
    return FxFallback.defaultFxRate(date);
  }

  // 현재 환율 조회 with fallback logic
  private FxRateDto getCurrentFxRateWithFallback() {
    try {
      FxRateDto latestRate = marketDataClientWrapper.getLatestFxRate();
      log.debug("최신 환율 조회 성공: {}", latestRate.rate());
      return latestRate;
    } catch (Exception e) {
      log.warn("최신 환율 조회 실패, 기본 환율 {}원 사용: {}", FxFallback.DEFAULT_RATE, e.getMessage());
      return FxFallback.defaultFxRate(LocalDate.now());
    }
  }

  // Holdings 기반 SimulationResponse 생성
  private SimulationResponse createHoldingSimulationResponse(HoldingDto holding,
    LocalDate purchaseDate,
    StockPriceDto currentPrice, BigDecimal currentValueUsd, BigDecimal currentValueKrw,
    BigDecimal stockReturn, BigDecimal stockReturnPercent, FxRateDto purchaseFxRate,
    FxRateDto currentFxRate, BigDecimal fxReturn, BigDecimal fxReturnPercent,
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
    if (Decimals.isZero(baseValue)) {
      log.warn("{}이(가) 0입니다. 수익률을 0으로 설정", valueName);
      return BigDecimal.ZERO;
    }
    return MoneyUtils.calculateReturnRate(baseValue, currentValue);
  }
}
