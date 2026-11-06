package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.engine.StrategyFinalizer;
import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.SkipReason;
import com.muscat.backtest.common.validation.BacktestRequestValidator;
import com.muscat.backtest.common.enums.type.InvestmentMode;
import com.muscat.backtest.common.enums.type.PriceDirection;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.logging.BacktestLogger;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.BacktestDataUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.common.util.FxRates;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.StrategyRequest;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.InvestmentParams;
import com.muscat.backtest.domain.model.PurchaseDecision;
import com.muscat.backtest.domain.model.SkippedRound;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 조건부 매수 전략: 첫 거래일에 사고, 그 뒤 전 거래일 종가보다 장중에 정한 비율만큼 떨어진(또는 오른) 날마다 한 번 삼(분할만 조정된 가격)
// 투자 방식은 TOTAL_BUDGET(총예산을 회차로 나눔) · PER_PURCHASE(회당 고정 금액 × 최대 횟수)
@Component
@RequiredArgsConstructor
@Slf4j
public class ConditionalPurchaseStrategy implements InvestmentStrategy {

  private final MarketDataClientWrapper marketData;
  private final ResponseMapper responseMapper;

  // 기준가로 살 때 매수가 소수 자릿수
  private static final int TRIGGER_PRICE_SCALE = 8;

  private static final PurchaseDecision NO_BUY = new PurchaseDecision(false, null, "");

  @Override
  public StrategyType getStrategyType() {
    return StrategyType.CONDITIONAL_PURCHASE;
  }

  @Override
  public StrategyResponse execute(StrategyRequest request) {
    // null 은 검증이 막음
    if (request != null && !(request instanceof ConditionalStrategyRequest)) {
      throw new BacktestException(BacktestResponse.STRATEGY_TYPE_MISMATCH,
        "조건부 매수 전략에 다른 요청이 왔습니다: " + request.getStrategyType());
    }
    return runConditional((ConditionalStrategyRequest) request);
  }

  private StrategyResponse runConditional(ConditionalStrategyRequest request) {
    validateConditionalRequest(request);

    BacktestLogger.setStrategyContext(request.getUserId(), "CONDITIONAL", request.getSymbol());

    try {
      log.info("조건부 매수 전략 실행 시작: {} - 전 거래일 종가 대비 {}% {} 시 매수 (모드: {})",
        request.getSymbol(), request.getDropPercentage().movePointRight(2),
        PriceDirection.orDrop(request.getDirection()).label(), request.getInvestmentMode());

      // 종료일이 미래인지 검증
      LocalDate today = LocalDate.now();
      LocalDate actualEndDate = request.getEndDate().isAfter(today) ? today : request.getEndDate();

      if (!actualEndDate.equals(request.getEndDate())) {
        log.info("종료일이 미래로 설정되어 오늘 날짜로 조정: {} -> {}", request.getEndDate(), actualEndDate);
      }

      // 투자 모드별 파라미터 계산 (회당 투자금, 최대 횟수)
      InvestmentParams params = calculateInvestmentParams(request);

      log.info("조건부 매수 전략 실행: 회당 {}원, 최대 {}회", params.amountPerPurchase(), params.maxPurchases());

      StrategyPurchases purchases = executeConditionalPurchases(request, actualEndDate, params);
      List<StrategyTransaction> transactions = purchases.transactions();

      if (transactions.isEmpty()) {
        throw new BacktestException(BacktestResponse.DATA_NOT_FOUND,
          "조건부 매수 전략 실행 가능한 데이터가 없습니다");
      }

      List<SkippedRound> skippedRounds = purchases.skippedRounds();

      // 마무리(평가일 시세/환율/배당재투자/계산) — 공통 로직 위임
      StrategyFinalizer.Result finalized = StrategyFinalizer.run(
        marketData, request.getSymbol(), request.getStartDate(), actualEndDate, request.getCurrentFxRate(),
        request.getPurchaseFxRate(), Boolean.TRUE.equals(request.getReinvestDividends()),
        request.getDividendTaxRate(), transactions, purchases.priceMap(), purchases.dailyFx());

      log.info("조건부 매수 전략 실행 완료: {} - {}회 투자, 수익률 {}%",
        request.getSymbol(), finalized.transactions().size(),
        finalized.calculation().getTotalReturnPercent());

      return finalized.fill(responseMapper.toStrategyResponse(
        request, finalized.transactions(), finalized.calculation(), finalized.currentPrice(), skippedRounds));

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
        int maxPurchases = totalInvestment.divide(amountPerPurchase, 0, RoundingMode.DOWN)
          .intValue();
        return new InvestmentParams(amountPerPurchase, maxPurchases);
      }
    } else {
      // 모드2: 회당 고정 금액 방식
      return new InvestmentParams(request.getAmountPerPurchase(), request.getMaxPurchases());
    }
  }

  // 조건부 매수 실행(시세가 깨진 날 종가 0 이하 · 계산이 실패한 날은 사지 않고 이유를 남긴 뒤 다음 날 다시)
  private StrategyPurchases executeConditionalPurchases(
    ConditionalStrategyRequest request, LocalDate actualEndDate, InvestmentParams params) {

    List<StrategyTransaction> transactions = new ArrayList<>();
    BigDecimal previousClose = null;

    //  BULK API 사용: 전체 기간의 가격 데이터를 한 번에 조회
    log.info("조건부 매수 전략 - Bulk 데이터 조회 시작: {} ~ {}", request.getStartDate(), actualEndDate);
    java.util.Map<LocalDate, OHLCPriceDto> priceMap = BacktestDataUtils.buildPriceMap(
      marketData, request.getSymbol(), request.getStartDate(), actualEndDate);

    //  BULK API 사용: 전체 기간의 환율 데이터를 한 번에 조회 (수동 설정이 없는 경우만)
    FxRates fxRateMap = FxRates.EMPTY;
    if (request.getPurchaseFxRate() == null) {
      // 첫 시세일부터 평가일까지
      LocalDate firstPrice = priceMap.keySet().stream().min(LocalDate::compareTo).orElse(actualEndDate);
      fxRateMap = BacktestDataUtils.buildDailyFxRateMap(marketData, firstPrice, actualEndDate);
      log.info("환율 데이터 조회 완료: {}개", fxRateMap.size());
    }

    LocalDate currentDate = request.getStartDate();
    int purchaseCount = 0;
    List<SkippedRound> skippedRounds = new ArrayList<>();

    // 메모리에서 데이터 조회 (API 호출 없음)
    while (!currentDate.isAfter(actualEndDate) && purchaseCount < params.maxPurchases()) {
      OHLCPriceDto priceData = priceMap.get(currentDate);
      boolean usable = PriceLookup.isUsable(priceData);
      try {
        if (priceData != null && priceData.available() && !usable) {
          skippedRounds.add(new SkippedRound(currentDate, SkipReason.NO_PRICE));
        } else if (usable) {
          var purchaseDecision = transactions.isEmpty()
            ? new PurchaseDecision(true, PriceLookup.effectiveClose(priceData), "초기매수")
            : evaluatePurchaseCondition(previousClose, priceData, request.getDropPercentage(),
              PriceDirection.orDrop(request.getDirection()));

          if (purchaseDecision.shouldBuy()) {
            // 환율 조회 (메모리에서)
            BigDecimal purchaseFxRate = request.getPurchaseFxRate() != null
              ? request.getPurchaseFxRate() : fxRateMap.get(currentDate);

            // 수수료를 차감하고 남은 달러로 매수
            BigDecimal fee = BacktestCalculationUtils.calculatePurchaseFee(
              params.amountPerPurchase(), purchaseFxRate, request.getTradingFeeRate());
            BigDecimal shares = BacktestCalculationUtils.calculateSharesAfterFee(
              params.amountPerPurchase(), purchaseFxRate, purchaseDecision.price(), fee);

            StrategyTransaction transaction = StrategyTransaction.builder()
              .date(currentDate)
              .actualDate(priceData.date())
              .price(purchaseDecision.price())
              .shares(shares)
              .fee(fee)
              .amount(params.amountPerPurchase())
              .fxRate(purchaseFxRate)
              .defaultFxDate(fxRateMap.isDefault(currentDate) ? currentDate : null)
              .trigger(purchaseDecision.trigger())
              .build();

            transactions.add(transaction);
            purchaseCount++;
            log.debug("조건부 매수 실행: {} - {} ({}원 투자, {}주 매수)",
              currentDate, purchaseDecision.trigger(), params.amountPerPurchase(), shares);
          }
        }

      } catch (RuntimeException e) {
        skippedRounds.add(new SkippedRound(currentDate, SkipReason.CALCULATION_ERROR));
        log.warn("조건부 {} 매수 계산 실패, 사지 않음: {}", currentDate, e.getMessage());
      }

      if (usable) {
        previousClose = PriceLookup.effectiveClose(priceData);
      }
      currentDate = currentDate.plusDays(1);
    }

    log.info("조건부 매수 전략 완료: {}회 매수, 사지 못한 날 {}", transactions.size(), skippedRounds.size());
    return new StrategyPurchases(transactions, skippedRounds, priceMap, fxRateMap);
  }

  // 방향에 맞는 기준가를 넘은 날 매수, 둘 다 닿은 날은 시가부터 하락 기준가 아래면 하락 · 아니면 상승 기준가
  private PurchaseDecision evaluatePurchaseCondition(BigDecimal previousClose, OHLCPriceDto price,
    BigDecimal percentage, PriceDirection direction) {
    PurchaseDecision drop = direction == PriceDirection.RISE
      ? NO_BUY : crossed(previousClose, price, percentage, false);
    PurchaseDecision rise = direction == PriceDirection.DROP
      ? NO_BUY : crossed(previousClose, price, percentage, true);
    boolean gapDown = drop.shouldBuy() && positive(price.openPrice())
      && drop.price().compareTo(price.openPrice()) == 0;
    return rise.shouldBuy() && !gapDown ? rise : drop;
  }

  // 저가가 전 거래일 종가 × (1 - 비율) 이하(상승은 고가가 전 거래일 종가 × (1 + 비율) 이상)면 그 값, 시가부터 넘었으면 시가
  // 보는 쪽(저가 · 고가)이 없거나 0 이하인 날 · 종가가 조정종가뿐인 날은 시가와 그 값을 종가로 봄
  private PurchaseDecision crossed(BigDecimal previousClose, OHLCPriceDto price, BigDecimal percentage,
    boolean rise) {
    BigDecimal close = PriceLookup.effectiveClose(price);
    BigDecimal trigger = previousClose.multiply(
      rise ? BigDecimal.ONE.add(percentage) : BigDecimal.ONE.subtract(percentage));
    BigDecimal extreme = rise ? price.highPrice() : price.lowPrice();
    boolean hasRange = price.closePrice() != null && positive(extreme);
    if (!beyond(hasRange ? extreme : close, trigger, rise)) {
      return NO_BUY;
    }

    BigDecimal open = hasRange ? price.openPrice() : close;
    BigDecimal buyPrice = positive(open) && beyond(open, trigger, rise)
      ? open : trigger.setScale(TRIGGER_PRICE_SCALE, RoundingMode.HALF_UP);
    BigDecimal change = MoneyUtils.calculateReturnRate(previousClose, buyPrice).abs();
    return new PurchaseDecision(true, buyPrice, String.format(rise ? "%.1f%%상승" : "%.1f%%하락", change));
  }

  // 상승이면 기준가 이상, 하락이면 기준가 이하
  private static boolean beyond(BigDecimal value, BigDecimal trigger, boolean rise) {
    int compared = value.compareTo(trigger);
    return rise ? compared >= 0 : compared <= 0;
  }

  private static boolean positive(BigDecimal value) {
    return value != null && value.signum() > 0;
  }
}
