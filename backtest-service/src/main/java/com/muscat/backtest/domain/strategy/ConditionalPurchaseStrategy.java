package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.calculation.StrategyCalculationResult;
import com.muscat.backtest.common.calculation.StrategyCalculator;
import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.enums.type.InvestmentMode;
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
      log.info("조건부 매수 전략 실행 시작: {} - {}% 하락시 매수 (모드: {})",
          request.getSymbol(), request.getDropPercentage(), request.getInvestmentMode());

      // 종료일이 미래인지 검증
      LocalDate today = LocalDate.now();
      LocalDate actualEndDate = request.getEndDate().isAfter(today) ? today : request.getEndDate();

      if (!actualEndDate.equals(request.getEndDate())) {
        log.info("종료일이 미래로 설정되어 오늘 날짜로 조정: {} -> {}", request.getEndDate(), actualEndDate);
      }

      // 투자 모드별 파라미터 계산 (회당 투자금, 최대 횟수)
      InvestmentParams params = calculateInvestmentParams(request);

      log.info("조건부 매수 전략 실행: {}% 하락시 매수, 회당 {}원, 최대 {}회",
          request.getDropPercentage(), params.amountPerPurchase(), params.maxPurchases());

      List<StrategyTransaction> transactions = executeConditionalPurchases(
          request, actualEndDate, params);

      if (transactions.isEmpty()) {
        throw new BacktestException(BacktestResponse.DATA_NOT_FOUND,
            "조건부 매수 전략 실행 가능한 데이터가 없습니다");
      }

      var currentPrice = BacktestDataUtils.getCurrentPrice(marketDataClient, request.getSymbol());

      // 환율 조회 (수동 설정 우선)
      MarketDataClient.FxRate currentFxRate;
      if (request.getCurrentFxRate() != null) {
        currentFxRate = new MarketDataClient.FxRate(LocalDate.now(), request.getCurrentFxRate());
      } else {
        currentFxRate = BacktestDataUtils.getCurrentFxRate(marketDataClient);
      }

      // 배당금 조회 (첫 매수일부터 현재까지)
      LocalDate firstPurchaseDate = transactions.get(0).getDate();
      var dividendHistory = BacktestDataUtils.getDividendHistory(
          marketDataClient, request.getSymbol(), firstPurchaseDate, LocalDate.now());

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

      // 재투자된 배당금 추적용 변수
      final BigDecimal[] dividendsReinvestedArray = {BigDecimal.ZERO};

      // 배당금 재투자 처리 (각 배당일에 순차적으로 재투자)
      if (Boolean.TRUE.equals(request.getReinvestDividends()) &&
          dividendHistory != null &&
          dividendHistory.getDividends() != null &&
          !dividendHistory.getDividends().isEmpty()) {

        log.info("배당금 재투자 실행 시작: {} 개의 배당 내역", dividendHistory.getDividends().size());

        // 배당 내역을 날짜순 정렬 후 순차 재투자
        dividendHistory.getDividends().stream()
            .filter(dividend -> dividend.getExDate() != null)
            .filter(dividend -> !dividend.getExDate().isBefore(firstPurchaseDate) &&
                              !dividend.getExDate().isAfter(LocalDate.now()))
            .sorted((d1, d2) -> d1.getExDate().compareTo(d2.getExDate()))
            .forEach(dividend -> {
              // 배당 지급 시점의 보유 주식수 계산
              BigDecimal sharesAtDividendDate = transactions.stream()
                  .filter(tx -> !tx.getActualDate().isAfter(dividend.getExDate()))
                  .map(StrategyTransaction::getShares)
                  .reduce(BigDecimal.ZERO, BigDecimal::add);

              if (sharesAtDividendDate.compareTo(BigDecimal.ZERO) > 0) {
                // 배당금 계산
                BigDecimal dividendAmount = dividend.getAmount().multiply(sharesAtDividendDate)
                    .setScale(2, java.math.RoundingMode.HALF_UP);

                // 배당 원천징수세 적용
                BigDecimal taxRate = request.getDividendTaxRate();
                BigDecimal afterTaxDividend = dividendAmount;
                if (taxRate != null && taxRate.compareTo(BigDecimal.ZERO) > 0) {
                  BigDecimal taxAmount = dividendAmount.multiply(taxRate)
                      .setScale(2, java.math.RoundingMode.HALF_UP);
                  afterTaxDividend = dividendAmount.subtract(taxAmount);
                  log.info("배당 원천징수: {} - 배당금 ${} → 세후 ${} (세율 {}%)",
                      dividend.getExDate(), dividendAmount, afterTaxDividend,
                      taxRate.multiply(BigDecimal.valueOf(100)));
                }

                // 배당금 지급일의 주가와 환율 조회
                var priceAtDividendDate = BacktestDataUtils.getHistoricalPrice(
                    marketDataClient, request.getSymbol(), dividend.getExDate());

                BigDecimal fxRateAtDividendDate;
                if (request.getPurchaseFxRate() != null) {
                  fxRateAtDividendDate = request.getPurchaseFxRate();
                } else {
                  var fxData = BacktestDataUtils.getHistoricalFxRate(marketDataClient, dividend.getExDate());
                  fxRateAtDividendDate = fxData != null ? fxData.rate() : currentFxRate.rate();
                }

                // 세후 배당금으로 추가 매수 가능한 주식수 계산
                BigDecimal additionalShares = afterTaxDividend.divide(
                    priceAtDividendDate.getClosePrice(), 8, java.math.RoundingMode.HALF_UP);

                // 배당 재투자 거래 기록 추가
                StrategyTransaction dividendReinvestment = StrategyTransaction.builder()
                    .date(dividend.getExDate())
                    .actualDate(priceAtDividendDate.getDate())
                    .price(priceAtDividendDate.getClosePrice())
                    .shares(additionalShares)
                    .amount(BigDecimal.ZERO)  // 배당금 재투자이므로 추가 투자금 없음
                    .fxRate(fxRateAtDividendDate)
                    .trigger("배당 재투자")
                    .build();

                transactions.add(dividendReinvestment);
                dividendsReinvestedArray[0] = dividendsReinvestedArray[0].add(afterTaxDividend);

                log.info("배당 재투자: {} - ${} ({}주 보유) -> {}주 추가 매수 @${}",
                    dividend.getExDate(), afterTaxDividend, sharesAtDividendDate,
                    additionalShares, priceAtDividendDate.getClosePrice());
              }
            });

        log.info("배당금 재투자 완료: 총 ${} 재투자", dividendsReinvestedArray[0]);
      }

      BigDecimal dividendsReinvested = dividendsReinvestedArray[0];

      // 배당 재투자 후 총 보유 주식수 재계산
      totalShares = transactions.stream()
          .map(StrategyTransaction::getShares)
          .reduce(BigDecimal.ZERO, BigDecimal::add);

      // 총 배당금 계산 (표시용, 재투자 여부와 무관)
      BigDecimal totalDividends = BacktestCalculationUtils.calculateTotalDividends(
          dividendHistory, totalShares, firstPurchaseDate, LocalDate.now());

      StrategyCalculationResult calculation = StrategyCalculator.calculate(
          transactions, totalInvested, totalShares, totalFxRateSum, currentPrice, currentFxRate,
          totalDividends, dividendsReinvested);

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
    InvestmentMode mode = request.getInvestmentMode();
    if (mode == null) {
      mode = InvestmentMode.TOTAL_BUDGET; // 기본값
    }

    if (mode == InvestmentMode.TOTAL_BUDGET) {
      // TOTAL_BUDGET 모드: totalInvestment 필수
      if (request.getTotalInvestment() == null
          || request.getTotalInvestment().compareTo(BigDecimal.ZERO) <= 0) {
        throw new BacktestException(BacktestResponse.STRATEGY_TOTAL_INVESTMENT_REQUIRED);
      }
    } else if (mode == InvestmentMode.PER_PURCHASE) {
      // PER_PURCHASE 모드: amountPerPurchase와 maxPurchases 필수
      if (request.getAmountPerPurchase() == null
          || request.getAmountPerPurchase().compareTo(BigDecimal.ZERO) <= 0) {
        throw new BacktestException(BacktestResponse.STRATEGY_TOTAL_INVESTMENT_REQUIRED,
            "회당 투자금액은 필수입니다");
      }
      if (request.getMaxPurchases() == null || request.getMaxPurchases() <= 0) {
        throw new BacktestException(BacktestResponse.INVALID_MAX_PURCHASES,
            "PER_PURCHASE 모드에서는 최대 매수 횟수가 필수입니다");
      }
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

  // 투자 모드별 파라미터 계산
  private InvestmentParams calculateInvestmentParams(ConditionalStrategyRequest request) {
    InvestmentMode mode = request.getInvestmentMode();
    if (mode == null) {
      mode = InvestmentMode.TOTAL_BUDGET;
    }

    if (mode == InvestmentMode.TOTAL_BUDGET) {
      // 모드1: 총 예산 분할 방식
      BigDecimal totalInvestment = request.getTotalInvestment();
      BigDecimal amountPerPurchase = request.getAmountPerPurchase();

      if (amountPerPurchase == null || amountPerPurchase.compareTo(BigDecimal.ZERO) <= 0) {
        // 회당 투자금 미설정 시 기본 10회 분할
        int defaultMaxPurchases = request.getMaxPurchases() != null ? request.getMaxPurchases() : 10;
        amountPerPurchase = BacktestCalculationUtils.calculateInvestmentPerDivision(
            totalInvestment, defaultMaxPurchases);
        return new InvestmentParams(amountPerPurchase, defaultMaxPurchases);
      } else {
        // 회당 투자금 기준으로 최대 매수 횟수 계산
        int maxPurchases = totalInvestment.divide(amountPerPurchase, 0, BigDecimal.ROUND_DOWN).intValue();
        return new InvestmentParams(amountPerPurchase, maxPurchases);
      }
    } else {
      // 모드2: 회당 고정 금액 방식
      return new InvestmentParams(request.getAmountPerPurchase(), request.getMaxPurchases());
    }
  }

  // 조건부 매수 실행
  private List<StrategyTransaction> executeConditionalPurchases(
      ConditionalStrategyRequest request, LocalDate actualEndDate, InvestmentParams params) {

    List<StrategyTransaction> transactions = new ArrayList<>();
    BigDecimal lastPrice = null;

    LocalDate currentDate = request.getStartDate();
    int purchaseCount = 0;

    while (!currentDate.isAfter(actualEndDate) && purchaseCount < params.maxPurchases()) {
      try {
        var priceData = BacktestDataUtils.getHistoricalPrice(
            marketDataClient, request.getSymbol(), currentDate);

        if (priceData.isAvailable()) {
          BigDecimal currentPriceValue = priceData.getClosePrice();
          var purchaseDecision = evaluatePurchaseCondition(lastPrice, currentPriceValue,
              request.getDropPercentage());

          if (purchaseDecision.shouldBuy()) {
            var transaction = createTransaction(request, currentDate, priceData,
                params.amountPerPurchase(), purchaseDecision.trigger());

            if (transaction != null) {
              transactions.add(transaction);
              purchaseCount++;
              log.debug("조건부 매수 실행: {} - {} ({}원 투자, {}주 매수)",
                  currentDate, purchaseDecision.trigger(), params.amountPerPurchase(),
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

    // 하락률 계산 및 비교
    BigDecimal dropPercent = MoneyUtils.calculateReturnRate(currentPrice, lastPrice).abs();
    BigDecimal dropPercentageAsPercent = dropPercentage.multiply(BigDecimal.valueOf(100));

    if (dropPercent.compareTo(dropPercentageAsPercent) >= 0) {
      return new PurchaseDecision(true, String.format("%.1f%%하락", dropPercent));
    }

    return new PurchaseDecision(false, "");
  }

  private StrategyTransaction createTransaction(ConditionalStrategyRequest request,
      LocalDate currentDate, OHLCPriceDto priceData, BigDecimal investmentPerPurchase, String trigger) {
    // 환율 조회 (수동 설정 우선)
    BigDecimal purchaseFxRate;
    if (request.getPurchaseFxRate() != null) {
      purchaseFxRate = request.getPurchaseFxRate();
    } else {
      var fxData = BacktestDataUtils.getHistoricalFxRate(marketDataClient, currentDate);
      if (fxData == null) {
        return null;
      }
      purchaseFxRate = fxData.rate();
    }

    BigDecimal shares = BacktestCalculationUtils.calculateShares(
        investmentPerPurchase, purchaseFxRate, priceData.getClosePrice());

    return StrategyTransaction.builder()
        .date(currentDate)
        .actualDate(priceData.getDate())
        .price(priceData.getClosePrice())
        .shares(shares)
        .amount(investmentPerPurchase)
        .fxRate(purchaseFxRate)
        .trigger(trigger)
        .build();
  }

  private record PurchaseDecision(boolean shouldBuy, String trigger) {

  }

  // 투자 파라미터 레코드
  private record InvestmentParams(BigDecimal amountPerPurchase, int maxPurchases) {
  }

}
