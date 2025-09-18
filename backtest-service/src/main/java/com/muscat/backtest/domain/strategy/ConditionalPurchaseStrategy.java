package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.calculation.StrategyCalculationResult;
import com.muscat.backtest.common.calculation.StrategyCalculator;
import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.logging.BacktestLogger;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.BacktestDataUtils;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.MarketDataClient;
import com.muscat.backtest.infra.client.dto.OHLCPriceDto;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 조건부 매수 전략 - 주가가 지정된 비율 이상 하락했을 때 분할 매수를 진행
@Component
@RequiredArgsConstructor
@Slf4j
public class ConditionalPurchaseStrategy implements InvestmentStrategy {

  private final MarketDataClient marketDataClient;
  private final ResponseMapper responseMapper;

  @Override
  public StrategyType getStrategyType() {
    return StrategyType.CONDITIONAL_PURCHASE;
  }

  @Override
  public StrategyResponse executeConditional(ConditionalStrategyRequest request) {
    validateConditionalRequest(request);

    BacktestLogger.setStrategyContext(request.getUserId(), "CONDITIONAL", request.getSymbol());

    try {
      log.info("조건부 매수 전략 실행 시작: {} - {}% 하락시 매수",
          request.getSymbol(), request.getDropPercentage());

      // 종료일이 미래인지 검증
      LocalDate today = LocalDate.now();
      LocalDate actualEndDate = request.getEndDate().isAfter(today) ? today : request.getEndDate();

      if (!actualEndDate.equals(request.getEndDate())) {
        log.info("종료일이 미래로 설정되어 오늘 날짜로 조정: {} -> {}", request.getEndDate(), actualEndDate);
      }

      int maxPurchases = request.getMaxPurchases() != null ? request.getMaxPurchases() : 10;

      log.info("조건부 매수 전략 실행: {}% 하락시 매수, 최대 {}회",
          request.getDropPercentage(), maxPurchases);

      List<StrategyTransaction> transactions = executeConditionalPurchases(
          request, actualEndDate, maxPurchases);

      if (transactions.isEmpty()) {
        throw new BacktestException(BacktestResponse.DATA_NOT_FOUND,
            "조건부 매수 전략 실행 가능한 데이터가 없습니다");
      }

      var currentPrice = BacktestDataUtils.getCurrentPrice(marketDataClient, request.getSymbol());
      var currentFxRate = BacktestDataUtils.getCurrentFxRate(marketDataClient);

      // 결과 계산
      BigDecimal totalInvested = transactions.stream()
          .map(StrategyTransaction::getAmount)
          .reduce(BigDecimal.ZERO, BigDecimal::add);

      BigDecimal totalShares = transactions.stream()
          .map(StrategyTransaction::getShares)
          .reduce(BigDecimal.ZERO, BigDecimal::add);

      BigDecimal totalFxRateSum = transactions.stream()
          .map(StrategyTransaction::getFxRate)
          .reduce(BigDecimal.ZERO, BigDecimal::add);

      StrategyCalculationResult calculation = StrategyCalculator.calculate(
          transactions, totalInvested, totalShares, totalFxRateSum, currentPrice, currentFxRate);

      log.info("조건부 매수 전략 실행 완료: {} - {}회 투자, 수익률 {}%",
          request.getSymbol(), transactions.size(), calculation.getTotalReturnPercent());

      return responseMapper.toStrategyResponse(request, transactions, calculation, currentPrice);

    } finally {
      BacktestLogger.remove("operation");
    }
  }

  // 조건부 매수 전략 특화 요청 검증
  private void validateConditionalRequest(ConditionalStrategyRequest request) {
    validateRequestNotNull(request);
    validateBasicFields(request);
    validateDateRange(request);
    validateInvestmentAmount(request);
    validateConditionalSpecificFields(request);
  }

  private void validateRequestNotNull(ConditionalStrategyRequest request) {
    if (request == null) {
      throw new BacktestException(BacktestResponse.STRATEGY_REQUEST_NULL);
    }
  }

  private void validateBasicFields(ConditionalStrategyRequest request) {
    if (request.getSymbol() == null || request.getSymbol().trim().isEmpty()) {
      throw new BacktestException(BacktestResponse.STRATEGY_SYMBOL_REQUIRED);
    }
  }

  private void validateDateRange(ConditionalStrategyRequest request) {
    if (request.getStartDate() == null) {
      throw new BacktestException(BacktestResponse.STRATEGY_START_DATE_REQUIRED);
    }
    if (request.getEndDate() == null) {
      throw new BacktestException(BacktestResponse.STRATEGY_END_DATE_REQUIRED);
    }
    if (request.getStartDate().isAfter(request.getEndDate())) {
      throw new BacktestException(BacktestResponse.STRATEGY_DATE_RANGE_INVALID);
    }
  }

  private void validateInvestmentAmount(ConditionalStrategyRequest request) {
    if (request.getTotalInvestment() == null
        || request.getTotalInvestment().compareTo(BigDecimal.ZERO) <= 0) {
      throw new BacktestException(BacktestResponse.STRATEGY_TOTAL_INVESTMENT_REQUIRED);
    }
  }

  private void validateConditionalSpecificFields(ConditionalStrategyRequest request) {
    if (request.getDropPercentage() == null
        || request.getDropPercentage().compareTo(BigDecimal.ZERO) <= 0) {
      throw new BacktestException(BacktestResponse.STRATEGY_DROP_PERCENTAGE_REQUIRED);
    }
    if (request.getMaxPurchases() != null && request.getMaxPurchases() <= 0) {
      throw new BacktestException(BacktestResponse.INVALID_MAX_PURCHASES);
    }
  }

  // 조건부 매수 실행
  private List<StrategyTransaction> executeConditionalPurchases(
      ConditionalStrategyRequest request, LocalDate actualEndDate, int maxPurchases) {

    List<StrategyTransaction> transactions = new ArrayList<>();
    BigDecimal lastPrice = null;
    BigDecimal investmentPerPurchase = BacktestCalculationUtils.calculateInvestmentPerDivision(
        request.getTotalInvestment(), maxPurchases);

    LocalDate currentDate = request.getStartDate();
    int purchaseCount = 0;

    while (!currentDate.isAfter(actualEndDate) && purchaseCount < maxPurchases) {
      try {
        var priceData = BacktestDataUtils.getHistoricalPrice(
            marketDataClient, request.getSymbol(), currentDate);

        if (priceData.isAvailable()) {
          BigDecimal currentPriceValue = priceData.getClosePrice();
          var purchaseDecision = evaluatePurchaseCondition(lastPrice, currentPriceValue,
              request.getDropPercentage());

          if (purchaseDecision.shouldBuy()) {
            var transaction = createTransaction(request, currentDate, priceData,
                investmentPerPurchase, purchaseDecision.trigger());

            if (transaction != null) {
              transactions.add(transaction);
              purchaseCount++;
              log.debug("조건부 매수 실행: {} - {} ({}원 투자, {}주 매수)",
                  currentDate, purchaseDecision.trigger(), investmentPerPurchase,
                  transaction.getShares());
            }
          }
          lastPrice = currentPriceValue;
        }

      } catch (Exception e) {
        log.warn("조건부 매수 전략 - {} 날짜 데이터 처리 실패: {}", currentDate, e.getMessage());
      }

      currentDate = currentDate.plusDays(1);
    }

    return transactions;
  }

  private PurchaseDecision evaluatePurchaseCondition(BigDecimal lastPrice, BigDecimal currentPrice,
      BigDecimal dropPercentage) {
    if (lastPrice == null) {
      return new PurchaseDecision(true, "초기매수");
    }

    BigDecimal dropPercent = MoneyUtils.calculateReturnRate(currentPrice, lastPrice).abs();
    if (dropPercent.compareTo(dropPercentage) >= 0) {
      return new PurchaseDecision(true, String.format("%.1f%%하락", dropPercent));
    }

    return new PurchaseDecision(false, "");
  }

  private StrategyTransaction createTransaction(ConditionalStrategyRequest request,
      LocalDate currentDate, OHLCPriceDto priceData, BigDecimal investmentPerPurchase, String trigger) {
    var fxData = BacktestDataUtils.getHistoricalFxRate(marketDataClient, currentDate);
    if (fxData == null) {
      return null;
    }

    BigDecimal shares = BacktestCalculationUtils.calculateShares(
        investmentPerPurchase, fxData.rate(), priceData.getClosePrice());

    return StrategyTransaction.builder()
        .date(currentDate)
        .actualDate(priceData.getDate())
        .price(priceData.getClosePrice())
        .shares(shares)
        .amount(investmentPerPurchase)
        .fxRate(fxData.rate())
        .trigger(trigger)
        .build();
  }

  private record PurchaseDecision(boolean shouldBuy, String trigger) {

  }

}