package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.constants.BacktestConstants;
import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.logging.BacktestLogger;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.BacktestDataUtils;
import com.muscat.backtest.domain.dto.request.OptimalTimingRequest;
import com.muscat.backtest.domain.dto.response.OptimalTimingResponse;
import com.muscat.backtest.domain.dto.response.TimingResult;
import com.muscat.backtest.infra.client.MarketDataClient;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 최적 매수 타이밍 분석
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OptimalTimingStrategy {

  private final MarketDataClient marketDataClient;

  public OptimalTimingResponse analyzeOptimalTiming(OptimalTimingRequest request) {
    validateRequest(request);

    BacktestLogger.setStrategyContext(request.getUserId(), "OPTIMAL_TIMING", request.getSymbol());

    try {
      log.info("최적 타이밍 분석 시작: {} - 목표 수익률 {}%", request.getSymbol(),
        request.getTargetReturnPercent());

      // 현재 주가 및 환율 조회
      LocalDate today = LocalDate.now();
      LocalDate actualEndDate = request.getEndDate().isAfter(today) ? today : request.getEndDate();
      var currentPrice = BacktestDataUtils.getCurrentPrice(marketDataClient, request.getSymbol());
      var currentFxRate = BacktestDataUtils.getCurrentFxRate(marketDataClient);

      log.info("현재 주가: ${}, 현재 환율: {}", currentPrice.currentPrice(), currentFxRate.rate());

      // BULK API: 전체 기간의 가격 데이터를 한 번에 조회
      log.info("최적 타이밍 분석 - Bulk 데이터 조회 시작: {} ~ {}", request.getStartDate(), actualEndDate);
      List<OHLCPriceDto> allPrices = marketDataClient.getOHLCPriceRange(
        request.getSymbol(),
        request.getStartDate().toString(),
        actualEndDate.toString()
      );

      // 날짜별 빠른 조회를 위한 Map 생성
      Map<LocalDate, OHLCPriceDto> priceMap = allPrices.stream()
        .filter(OHLCPriceDto::available)
        .collect(Collectors.toMap(OHLCPriceDto::date, p -> p, (existing, replacement) -> replacement));

      log.info("가격 데이터 조회 완료: {}개", priceMap.size());

      // BULK API: 전체 기간의 환율 데이터를 한 번에 조회
      List<LocalDate> allDates = new ArrayList<>();
      LocalDate date = request.getStartDate();
      while (!date.isAfter(actualEndDate)) {
        allDates.add(date);
        date = date.plusDays(1);
      }

      Map<LocalDate, BigDecimal> fxRateMap =
        BacktestDataUtils.getBulkFxRates(marketDataClient, allDates);
      log.info("환율 데이터 조회 완료: {}개", fxRateMap.size());

      // 메모리에서 데이터 조회하여 분석 수행 (API 호출 없음)
      List<TimingResult> allResults = new ArrayList<>();
      int analyzedDays = 0;

      LocalDate currentDate = request.getStartDate();
      while (!currentDate.isAfter(actualEndDate)) {
        OHLCPriceDto priceData = priceMap.get(currentDate);
        BigDecimal fxRate = fxRateMap.get(currentDate);

        if (priceData != null && priceData.available() && fxRate != null) {
          TimingResult result = calculateTimingResult(
            request, currentDate, priceData.closePrice(), fxRate,
            currentPrice.currentPrice(), currentFxRate.rate());

          allResults.add(result);
          analyzedDays++;
        }

        currentDate = currentDate.plusDays(1);
      }

      if (allResults.isEmpty()) {
        throw new BacktestException(BacktestResponse.DATA_NOT_FOUND,
          "분석 가능한 데이터가 없습니다");
      }

      // 목표 수익률 달성 타이밍만 필터링 및 수익률순 정렬
      List<TimingResult> qualifyingTimings = allResults.stream()
        .filter(r -> r.getTotalReturnPercent().compareTo(request.getTargetReturnPercent()) >= 0)
        .sorted(Comparator.comparing(TimingResult::getTotalReturnPercent).reversed())
        .collect(Collectors.toList());

      // 수익률 순위 부여
      for (int i = 0; i < qualifyingTimings.size(); i++) {
        TimingResult timing = qualifyingTimings.get(i);
        qualifyingTimings.set(i, TimingResult.builder()
          .purchaseDate(timing.getPurchaseDate())
          .purchasePrice(timing.getPurchasePrice())
          .purchaseFxRate(timing.getPurchaseFxRate())
          .shares(timing.getShares())
          .currentValue(timing.getCurrentValue())
          .currentValueKrw(timing.getCurrentValueKrw())
          .totalReturn(timing.getTotalReturn())
          .totalReturnPercent(timing.getTotalReturnPercent())
          .stockReturn(timing.getStockReturn())
          .stockReturnPercent(timing.getStockReturnPercent())
          .fxReturn(timing.getFxReturn())
          .fxReturnPercent(timing.getFxReturnPercent())
          .rank(i + 1)
          .build());
      }

      TimingResult bestTiming = qualifyingTimings.isEmpty() ? null : qualifyingTimings.get(0);

      log.info("최적 타이밍 분석 완료: 전체 {}일 중 {}일이 목표 수익률 이상 달성",
        analyzedDays, qualifyingTimings.size());

      return OptimalTimingResponse.builder()
        .symbol(request.getSymbol())
        .analysisDate(today)
        .targetReturnPercent(request.getTargetReturnPercent())
        .qualifyingTimings(qualifyingTimings)
        .bestTiming(bestTiming)
        .totalQualifyingDays(qualifyingTimings.size())
        .totalAnalyzedDays(analyzedDays)
        .investmentAmount(request.getInvestmentAmount())
        .currentPrice(currentPrice.currentPrice())
        .currentFxRate(currentFxRate.rate())
        .build();

    } finally {
      BacktestLogger.remove("operation");
    }
  }

  private TimingResult calculateTimingResult(OptimalTimingRequest request, LocalDate purchaseDate,
    BigDecimal purchasePrice, BigDecimal purchaseFxRate,
    BigDecimal currentPrice, BigDecimal currentFxRate) {

    // 매수 가능 주식수 계산
    BigDecimal shares = BacktestCalculationUtils.calculateShares(
      request.getInvestmentAmount(), purchaseFxRate, purchasePrice);

    // 현재 평가금액 (USD)
    BigDecimal currentValue = shares.multiply(currentPrice)
      .setScale(BacktestConstants.Money.SCALE, BacktestConstants.Money.ROUNDING_MODE);

    // 현재 평가금액 (KRW)
    BigDecimal currentValueKrw = currentValue.multiply(currentFxRate)
      .setScale(0, BacktestConstants.Money.ROUNDING_MODE);

    // 총 수익금 (KRW)
    BigDecimal totalReturn = currentValueKrw.subtract(request.getInvestmentAmount());

    // 총 수익률 계산 (%)
    BigDecimal totalReturnPercent = MoneyUtils.calculateReturnRate(
      request.getInvestmentAmount(), currentValueKrw);

    // 주식 수익금 (USD)
    BigDecimal purchaseValue = shares.multiply(purchasePrice)
      .setScale(BacktestConstants.Money.SCALE, BacktestConstants.Money.ROUNDING_MODE);
    BigDecimal stockReturn = currentValue.subtract(purchaseValue);

    // 주식 수익률 (%)
    BigDecimal stockReturnPercent = MoneyUtils.calculateReturnRate(currentPrice, purchasePrice);

    // 환차익 계산
    BigDecimal fxReturn = currentValue.multiply(currentFxRate.subtract(purchaseFxRate))
      .setScale(BacktestConstants.Money.SCALE, BacktestConstants.Money.ROUNDING_MODE);

    BigDecimal fxReturnPercent = MoneyUtils.calculateReturnRate(currentFxRate, purchaseFxRate);

    return TimingResult.builder()
      .purchaseDate(purchaseDate)
      .purchasePrice(purchasePrice)
      .purchaseFxRate(purchaseFxRate)
      .shares(shares)
      .currentValue(currentValue)
      .currentValueKrw(currentValueKrw)
      .totalReturn(totalReturn)
      .totalReturnPercent(totalReturnPercent)
      .stockReturn(stockReturn)
      .stockReturnPercent(stockReturnPercent)
      .fxReturn(fxReturn)
      .fxReturnPercent(fxReturnPercent)
      .build();
  }

  private void validateRequest(OptimalTimingRequest request) {
    if (request == null) {
      throw new BacktestException(BacktestResponse.STRATEGY_REQUEST_NULL);
    }
    if (request.getSymbol() == null || request.getSymbol().trim().isEmpty()) {
      throw new BacktestException(BacktestResponse.STRATEGY_SYMBOL_REQUIRED);
    }
    if (request.getStartDate() == null) {
      throw new BacktestException(BacktestResponse.STRATEGY_START_DATE_REQUIRED);
    }
    if (request.getEndDate() == null) {
      throw new BacktestException(BacktestResponse.STRATEGY_END_DATE_REQUIRED);
    }
    if (request.getStartDate().isAfter(request.getEndDate())) {
      throw new BacktestException(BacktestResponse.STRATEGY_DATE_RANGE_INVALID);
    }
    if (request.getInvestmentAmount() == null
      || request.getInvestmentAmount().compareTo(BigDecimal.ZERO) <= 0) {
      throw new BacktestException(BacktestResponse.INVALID_REQUEST,
        "투자 금액은 0보다 커야 합니다");
    }
    if (request.getTargetReturnPercent() == null) {
      throw new BacktestException(BacktestResponse.INVALID_REQUEST,
        "목표 수익률을 입력해주세요");
    }
  }
}
