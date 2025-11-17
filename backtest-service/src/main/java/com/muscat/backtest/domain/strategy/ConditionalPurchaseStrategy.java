package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.calculation.StrategyCalculationResult;
import com.muscat.backtest.common.calculation.StrategyCalculator;
import com.muscat.backtest.common.constants.BacktestConstants;
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
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
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
      LocalDate firstPurchaseDate = transactions.getFirst().getDate();
      var dividendHistory = BacktestDataUtils.getDividendHistory(
        marketDataClient, request.getSymbol(), firstPurchaseDate, LocalDate.now());

      // 결과 계산
      BigDecimal totalInvested = transactions.stream()
        .map(StrategyTransaction::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

      BigDecimal totalShares = transactions.stream()
        .map(StrategyTransaction::getShares)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

      // 투자금액으로 가중평균한 환율 합계 계산 (가중평균 환율 = Σ(투자금액 × 환율) / Σ투자금액)
      BigDecimal totalFxRateSum = transactions.stream()
        .map(t -> t.getFxRate().multiply(t.getAmount()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);

      // 재투자된 배당금 추적용 변수
      final BigDecimal[] dividendsReinvestedArray = {BigDecimal.ZERO};

      // 배당금 재투자 처리 (각 배당일에 순차적으로 재투자)
      if (Boolean.TRUE.equals(request.getReinvestDividends()) &&
        dividendHistory != null &&
        dividendHistory.getDividends() != null &&
        !dividendHistory.getDividends().isEmpty()) {

        log.info("배당금 재투자 실행 시작: {} 개의 배당 내역", dividendHistory.getDividends().size());

        // ✅ BULK API: 배당 날짜들의 가격 및 환율 데이터를 한 번에 조회
        List<LocalDate> dividendDates = dividendHistory.getDividends().stream()
          .filter(dividend -> dividend.getExDate() != null)
          .filter(dividend -> !dividend.getExDate().isBefore(firstPurchaseDate) &&
            !dividend.getExDate().isAfter(LocalDate.now()))
          .map(d -> d.getExDate())
          .toList();

        if (!dividendDates.isEmpty()) {
          // 배당 날짜들의 가격 데이터 조회
          List<OHLCPriceDto> dividendPrices = marketDataClient.getOHLCPriceRange(
            request.getSymbol(),
            dividendDates.get(0).toString(),
            dividendDates.get(dividendDates.size() - 1).toString()
          );

          java.util.Map<LocalDate, OHLCPriceDto> dividendPriceMap = dividendPrices.stream()
            .filter(OHLCPriceDto::available)
            .collect(java.util.stream.Collectors.toMap(OHLCPriceDto::date, p -> p));

          // 배당 날짜들의 환율 데이터 조회
          java.util.Map<LocalDate, BigDecimal> dividendFxRateMap = new java.util.HashMap<>();
          if (request.getPurchaseFxRate() == null) {
            dividendFxRateMap = BacktestDataUtils.getBulkFxRates(marketDataClient, dividendDates);
          }

          log.info("배당 재투자 데이터 조회 완료: 가격 {}개, 환율 {}개",
            dividendPriceMap.size(), dividendFxRateMap.size());

          // ✅ 메모리에서 데이터 조회하여 배당 재투자 실행
          dividendHistory.getDividends().stream()
            .filter(dividend -> dividend.getExDate() != null)
            .filter(dividend -> !dividend.getExDate().isBefore(firstPurchaseDate) &&
              !dividend.getExDate().isAfter(LocalDate.now()))
            .sorted((d1, d2) -> d1.getExDate().compareTo(d2.getExDate()))
            .forEach(dividend -> {
              // 배당 지급 시점의 보유 주식수 계산
              BigDecimal sharesAtDividendDate = transactions.stream()
                .filter(tx -> !tx.getDate().isAfter(dividend.getExDate()))
                .map(StrategyTransaction::getShares)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

              if (sharesAtDividendDate.compareTo(BigDecimal.ZERO) > 0) {
                // 배당금 계산
                BigDecimal dividendAmount = dividend.getAmount().multiply(sharesAtDividendDate)
                  .setScale(BacktestConstants.Money.SCALE, BacktestConstants.Money.ROUNDING_MODE);

                // 배당 원천징수세 적용
                BigDecimal taxRate = request.getDividendTaxRate();
                BigDecimal afterTaxDividend = dividendAmount;
                if (taxRate != null && taxRate.compareTo(BigDecimal.ZERO) > 0) {
                  BigDecimal taxAmount = dividendAmount.multiply(taxRate)
                    .setScale(BacktestConstants.Money.SCALE, BacktestConstants.Money.ROUNDING_MODE);
                  afterTaxDividend = dividendAmount.subtract(taxAmount);
                  log.debug("배당 원천징수: {} - 배당금 ${} → 세후 ${} (세율 {}%)",
                    dividend.getExDate(), dividendAmount, afterTaxDividend,
                    taxRate.multiply(BacktestConstants.Money.PERCENTAGE_MULTIPLIER));
                }

                // 메모리에서 배당금 지급일의 주가와 환율 조회
                OHLCPriceDto priceAtDividendDate = dividendPriceMap.get(dividend.getExDate());

                BigDecimal fxRateAtDividendDate;
                if (request.getPurchaseFxRate() != null) {
                  fxRateAtDividendDate = request.getPurchaseFxRate();
                } else {
                  fxRateAtDividendDate = dividendFxRateMap.getOrDefault(
                    dividend.getExDate(), currentFxRate.rate());
                }

                if (priceAtDividendDate != null && priceAtDividendDate.available()) {
                  // 세후 배당금으로 추가 매수 가능한 주식수 계산
                  BigDecimal additionalShares = afterTaxDividend.divide(
                    priceAtDividendDate.closePrice(), 8, java.math.RoundingMode.HALF_UP);

                  // 배당 재투자 거래 기록 추가
                  StrategyTransaction dividendReinvestment = StrategyTransaction.builder()
                    .date(dividend.getExDate())
                    .actualDate(priceAtDividendDate.date())
                    .price(priceAtDividendDate.closePrice())
                    .shares(additionalShares)
                    .amount(BigDecimal.ZERO)  // 배당금 재투자이므로 추가 투자금 없음
                    .fxRate(fxRateAtDividendDate)
                    .trigger("배당 재투자")
                    .build();

                  transactions.add(dividendReinvestment);
                  dividendsReinvestedArray[0] = dividendsReinvestedArray[0].add(afterTaxDividend);

                  log.debug("배당 재투자: {} - ${} ({}주 보유) -> {}주 추가 매수 @${}",
                    dividend.getExDate(), afterTaxDividend, sharesAtDividendDate,
                    additionalShares, priceAtDividendDate.closePrice());
                }
              }
            });
        }

        log.info("배당금 재투자 완료: 총 ${} 재투자", dividendsReinvestedArray[0]);
      }

      BigDecimal dividendsReinvested = dividendsReinvestedArray[0];

      // 배당 재투자 후 총 보유 주식수 재계산
      totalShares = transactions.stream()
        .map(StrategyTransaction::getShares)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

      // 총 배당금 계산 (표시용, 재투자 여부와 무관)
      BigDecimal totalDividends = BacktestCalculationUtils.calculateTotalDividends(
        dividendHistory, transactions, firstPurchaseDate, LocalDate.now());

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
        int defaultMaxPurchases =
          request.getMaxPurchases() != null ? request.getMaxPurchases() : 10;
        amountPerPurchase = BacktestCalculationUtils.calculateInvestmentPerDivision(
          totalInvestment, defaultMaxPurchases);
        return new InvestmentParams(amountPerPurchase, defaultMaxPurchases);
      } else {
        // 회당 투자금 기준으로 최대 매수 횟수 계산
        int maxPurchases = totalInvestment.divide(amountPerPurchase, 0, BigDecimal.ROUND_DOWN)
          .intValue();
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

    // ✅ BULK API 사용: 전체 기간의 가격 데이터를 한 번에 조회
    log.info("조건부 매수 전략 - Bulk 데이터 조회 시작: {} ~ {}", request.getStartDate(), actualEndDate);
    List<OHLCPriceDto> allPrices = marketDataClient.getOHLCPriceRange(
      request.getSymbol(),
      request.getStartDate().toString(),
      actualEndDate.toString()
    );

    // 날짜별 빠른 조회를 위한 Map 생성
    java.util.Map<LocalDate, OHLCPriceDto> priceMap = allPrices.stream()
      .filter(OHLCPriceDto::available)
      .collect(java.util.stream.Collectors.toMap(OHLCPriceDto::date, p -> p));

    log.info("가격 데이터 조회 완료: {}개", priceMap.size());

    // ✅ BULK API 사용: 전체 기간의 환율 데이터를 한 번에 조회 (수동 설정이 없는 경우만)
    java.util.Map<LocalDate, BigDecimal> fxRateMap = new java.util.HashMap<>();
    if (request.getPurchaseFxRate() == null) {
      List<LocalDate> allDates = new ArrayList<>();
      LocalDate date = request.getStartDate();
      while (!date.isAfter(actualEndDate)) {
        allDates.add(date);
        date = date.plusDays(1);
      }

      fxRateMap = BacktestDataUtils.getBulkFxRates(marketDataClient, allDates);
      log.info("환율 데이터 조회 완료: {}개", fxRateMap.size());
    }

    LocalDate currentDate = request.getStartDate();
    int purchaseCount = 0;

    // ✅ 메모리에서 데이터 조회 (API 호출 없음)
    while (!currentDate.isAfter(actualEndDate) && purchaseCount < params.maxPurchases()) {
      try {
        OHLCPriceDto priceData = priceMap.get(currentDate);

        if (priceData != null && priceData.available()) {
          BigDecimal currentPriceValue = priceData.closePrice();
          var purchaseDecision = evaluatePurchaseCondition(lastPrice, currentPriceValue,
            request.getDropPercentage());

          if (purchaseDecision.shouldBuy()) {
            // 환율 조회 (메모리에서)
            BigDecimal purchaseFxRate;
            if (request.getPurchaseFxRate() != null) {
              purchaseFxRate = request.getPurchaseFxRate();
            } else {
              purchaseFxRate = fxRateMap.get(currentDate);
              if (purchaseFxRate == null) {
                log.warn("환율 데이터 없음: {}", currentDate);
                currentDate = currentDate.plusDays(1);
                continue;
              }
            }

            BigDecimal shares = BacktestCalculationUtils.calculateShares(
              params.amountPerPurchase(), purchaseFxRate, priceData.closePrice());

            StrategyTransaction transaction = StrategyTransaction.builder()
              .date(currentDate)
              .actualDate(priceData.date())
              .price(priceData.closePrice())
              .shares(shares)
              .amount(params.amountPerPurchase())
              .fxRate(purchaseFxRate)
              .trigger(purchaseDecision.trigger())
              .build();

            transactions.add(transaction);
            purchaseCount++;
            log.debug("조건부 매수 실행: {} - {} ({}원 투자, {}주 매수)",
              currentDate, purchaseDecision.trigger(), params.amountPerPurchase(), shares);
          }
          lastPrice = currentPriceValue;
        }

      } catch (Exception e) {
        log.warn("조건부 매수 전략 - {} 날짜 데이터 처리 실패: {}", currentDate, e.getMessage());
      }

      currentDate = currentDate.plusDays(1);
    }

    log.info("조건부 매수 전략 완료: {}회 매수", transactions.size());
    return transactions;
  }

  private PurchaseDecision evaluatePurchaseCondition(BigDecimal lastPrice, BigDecimal currentPrice,
    BigDecimal dropPercentage) {
    if (lastPrice == null) {
      return new PurchaseDecision(true, "초기매수");
    }

    // 하락률 계산 및 비교
    BigDecimal dropPercent = MoneyUtils.calculateReturnRate(currentPrice, lastPrice).abs();
    BigDecimal dropPercentageAsPercent = dropPercentage.multiply(BacktestConstants.Money.PERCENTAGE_MULTIPLIER);

    if (dropPercent.compareTo(dropPercentageAsPercent) >= 0) {
      return new PurchaseDecision(true, String.format("%.1f%%하락", dropPercent));
    }

    return new PurchaseDecision(false, "");
  }

  private StrategyTransaction createTransaction(ConditionalStrategyRequest request,
    LocalDate currentDate, OHLCPriceDto priceData, BigDecimal investmentPerPurchase,
    String trigger) {
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
      investmentPerPurchase, purchaseFxRate, priceData.closePrice());

    return StrategyTransaction.builder()
      .date(currentDate)
      .actualDate(priceData.date())
      .price(priceData.closePrice())
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
