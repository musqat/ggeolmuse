package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.constants.BacktestConstants;
import com.muscat.backtest.common.engine.StrategyFinalizer;
import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.validation.BacktestRequestValidator;
import com.muscat.backtest.common.enums.type.InvestmentMode;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.logging.BacktestLogger;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.BacktestDataUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.InvestmentParams;
import com.muscat.backtest.domain.model.PurchaseDecision;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.MarketDataClient;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 조건부 매수(저점 분할매수) 전략
 * 직전 매수가 대비 dropPercentage 이상 하락 시마다 분할 매수
 * 하락 판정·매수가 모두 조정종가 기준
 * 투자모드: TOTAL_BUDGET(총예산 회차분할) / PER_PURCHASE(회당 고정금액 × 최대횟수)
 * 배당재투자·환율·원천징수는
 */
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

  /**
   * 조건부 매수 실행 → 백테스트 결과
   * 시작일~종료일 순회, 직전 매수가 대비 하락률 기준 이상이면 매수
   */
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

      // 마무리(현재가/환율/배당재투자/계산) — 공통 로직 위임
      StrategyFinalizer.Result finalized = StrategyFinalizer.run(
        marketDataClient, request.getSymbol(), request.getCurrentFxRate(),
        request.getPurchaseFxRate(), Boolean.TRUE.equals(request.getReinvestDividends()),
        request.getDividendTaxRate(), transactions);

      log.info("조건부 매수 전략 실행 완료: {} - {}회 투자, 수익률 {}%",
        request.getSymbol(), finalized.transactions().size(),
        finalized.calculation().getTotalReturnPercent());

      return responseMapper.toStrategyResponse(
        request, finalized.transactions(), finalized.calculation(), finalized.currentPrice());

    } finally {
      BacktestLogger.remove("operation");
    }
  }

  // 조건부 매수 전략 특화 요청 검증
  private void validateConditionalRequest(ConditionalStrategyRequest request) {
    BacktestRequestValidator.requireNonNull(request);
    BacktestRequestValidator.requireSymbol(request.getSymbol());
    BacktestRequestValidator.requireDateRange(request.getStartDate(), request.getEndDate());
    validateInvestmentAmount(request);
    validateConditionalSpecificFields(request);
  }

  private void validateInvestmentAmount(ConditionalStrategyRequest request) {
    InvestmentMode mode = request.getInvestmentMode();
    if (mode == null) {
      mode = InvestmentMode.TOTAL_BUDGET; // 기본값
    }

    if (mode == InvestmentMode.TOTAL_BUDGET) {
      // TOTAL_BUDGET 모드: totalInvestment 필수
      if (request.getTotalInvestment() == null
        || !Decimals.isPositive(request.getTotalInvestment())) {
        throw new BacktestException(BacktestResponse.STRATEGY_TOTAL_INVESTMENT_REQUIRED);
      }
    } else if (mode == InvestmentMode.PER_PURCHASE) {
      // PER_PURCHASE 모드: amountPerPurchase와 maxPurchases 필수
      if (request.getAmountPerPurchase() == null
        || !Decimals.isPositive(request.getAmountPerPurchase())) {
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
      || !Decimals.isPositive(request.getDropPercentage())) {
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

      if (amountPerPurchase == null || !Decimals.isPositive(amountPerPurchase)) {
        // 회당 투자금 미설정 시 기본 10회 분할
        int defaultMaxPurchases =
          request.getMaxPurchases() != null ? request.getMaxPurchases() : 10;
        amountPerPurchase = BacktestCalculationUtils.calculateInvestmentPerDivision(
          totalInvestment, defaultMaxPurchases);
        return new InvestmentParams(amountPerPurchase, defaultMaxPurchases);
      } else {
        // 회당 투자금 기준으로 최대 매수 횟수 계산
        int maxPurchases = totalInvestment.divide(amountPerPurchase, 0, java.math.RoundingMode.DOWN)
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

    //  BULK API 사용: 전체 기간의 가격 데이터를 한 번에 조회
    log.info("조건부 매수 전략 - Bulk 데이터 조회 시작: {} ~ {}", request.getStartDate(), actualEndDate);
    java.util.Map<LocalDate, OHLCPriceDto> priceMap = BacktestDataUtils.buildPriceMap(
      marketDataClient, request.getSymbol(), request.getStartDate(), actualEndDate);

    //  BULK API 사용: 전체 기간의 환율 데이터를 한 번에 조회 (수동 설정이 없는 경우만)
    java.util.Map<LocalDate, BigDecimal> fxRateMap = new java.util.HashMap<>();
    if (request.getPurchaseFxRate() == null) {
      fxRateMap = BacktestDataUtils.buildDailyFxRateMap(
        marketDataClient, request.getStartDate(), actualEndDate);
      log.info("환율 데이터 조회 완료: {}개", fxRateMap.size());
    }

    LocalDate currentDate = request.getStartDate();
    int purchaseCount = 0;

    // 메모리에서 데이터 조회 (API 호출 없음)
    while (!currentDate.isAfter(actualEndDate) && purchaseCount < params.maxPurchases()) {
      try {
        OHLCPriceDto priceData = priceMap.get(currentDate);

        if (priceData != null && priceData.available()) {
          BigDecimal currentPriceValue = PriceLookup.effectiveClose(priceData);
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
              params.amountPerPurchase(), purchaseFxRate, PriceLookup.effectiveClose(priceData));

            StrategyTransaction transaction = StrategyTransaction.builder()
              .date(currentDate)
              .actualDate(priceData.date())
              .price(PriceLookup.effectiveClose(priceData))
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
      investmentPerPurchase, purchaseFxRate, PriceLookup.effectiveClose(priceData));

    return StrategyTransaction.builder()
      .date(currentDate)
      .actualDate(priceData.date())
      .price(PriceLookup.effectiveClose(priceData))
      .shares(shares)
      .amount(investmentPerPurchase)
      .fxRate(purchaseFxRate)
      .trigger(trigger)
      .build();
  }
}
