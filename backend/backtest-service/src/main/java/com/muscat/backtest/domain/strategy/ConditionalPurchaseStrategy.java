package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.calculation.TradeStatsCalculator;
import com.muscat.backtest.common.engine.CapitalGainsTaxPayer;
import com.muscat.backtest.common.engine.StrategyFinalizer;
import com.muscat.backtest.common.engine.YearEndHarvest;
import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.SkipReason;
import com.muscat.backtest.common.validation.BacktestRequestValidator;
import com.muscat.backtest.common.enums.type.PriceDirection;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.enums.type.TaxSale;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.logging.BacktestLogger;
import com.muscat.backtest.common.util.RoundPurchase;
import com.muscat.backtest.common.util.BacktestDataUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.common.util.FxRates;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.StrategyRequest;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.PurchaseDecision;
import com.muscat.backtest.domain.model.SkippedRound;
import com.muscat.backtest.domain.model.SkippedSpan;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 조건부 매수 전략: 전 거래일 종가보다 장중에 정한 비율만큼 떨어진(또는 오른) 날마다 회당 금액이나 정한 주식 수만큼 한 번 삼(분할만 조정된 가격, 기간 첫 거래일은 기준 종가만)
// 한도는 예산 · 최대 횟수 가운데 먼저 닿는 쪽(둘 다 비우면 제한 없음), 예산을 넘는 회차부터는 예산 부족 구간으로 셈
// 익절 · 손절은 보유분 평균 단가 기준으로 전량, 트레일링 스탑은 고점 대비 내린 날 · 부분 익절은 평균 단가 대비 단계만큼 오른 날 정한 몫을 팔고, 다음 조건이 오면 판 돈부터 써서 다시 삼
// 연말 정리를 켜면 결제가 그해 안에 끝나는 마지막 거래일에 그해 실현 이익이 공제에 맞도록 팔고 같은 값에 다시 삼(다시 산 값이 평균 단가에 들어감)
@Component
@RequiredArgsConstructor
@Slf4j
public class ConditionalPurchaseStrategy implements InvestmentStrategy {

  private final MarketDataClientWrapper marketData;
  private final ResponseMapper responseMapper;

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
      log.info("조건부 매수 전략 실행 시작: {} - 전 거래일 종가 대비 {}% {} 시 회당 {} 매수 (예산 {}, 최대 {}회)",
        request.getSymbol(), request.getDropPercentage().movePointRight(2),
        PriceDirection.orDrop(request.getDirection()).label(), request.perPurchase(),
        request.getTotalInvestment(), request.getMaxPurchases());

      // 종료일이 미래인지 검증
      LocalDate today = LocalDate.now();
      LocalDate actualEndDate = request.getEndDate().isAfter(today) ? today : request.getEndDate();

      if (!actualEndDate.equals(request.getEndDate())) {
        log.info("종료일이 미래로 설정되어 오늘 날짜로 조정: {} -> {}", request.getEndDate(), actualEndDate);
      }

      CapitalGainsTaxPayer taxPayer = new CapitalGainsTaxPayer(Boolean.TRUE.equals(request.getCapitalGainsTax()));
      StrategyPurchases purchases = executeConditionalPurchases(request, actualEndDate, taxPayer);
      List<StrategyTransaction> transactions = purchases.transactions();
      List<SkippedRound> skippedRounds = purchases.skippedRounds();

      // 마무리(평가일 시세/환율/배당재투자/계산) — 공통 로직 위임
      StrategyFinalizer.Result finalized = StrategyFinalizer.run(
        marketData, request.getSymbol(), request.getStartDate(), actualEndDate, request.getCurrentFxRate(),
        request.getPurchaseFxRate(), Boolean.TRUE.equals(request.getReinvestDividends()),
        request.getDividendTaxRate(), transactions, purchases.priceMap(), purchases.dailyFx(), request.getPeriodEnd(),
        request.getTradingFeeRate(), !sells(request) && harvests(request));

      log.info("조건부 매수 전략 실행 완료: {} - {}회 투자, 수익률 {}%",
        request.getSymbol(), finalized.transactions().size(),
        finalized.calculation().getTotalReturnPercent());

      StrategyResponse response = finalized.fill(responseMapper.toStrategyResponse(
        request, finalized.transactions(), finalized.calculation(), finalized.currentPrice(), skippedRounds));
      response.setSkippedSpan(purchases.skippedSpan());
      response.setCapitalGainsTax(Boolean.TRUE.equals(request.getCapitalGainsTax())
        ? finalized.capitalGainsTax(taxPayer.payments())
        : null);
      response.setTradeStats(TradeStatsCalculator.of(finalized.transactions(), request.getStartDate(), actualEndDate));
      return response;

    } finally {
      BacktestLogger.remove("operation");
    }
  }

  // 조건부 매수 전략 특화 요청 검증
  private void validateConditionalRequest(ConditionalStrategyRequest request) {
    BacktestRequestValidator.requireNonNull(request);
    BacktestRequestValidator.requireSymbol(request.getSymbol());
    BacktestRequestValidator.requireDateRange(request.getStartDate(), request.getEndDate());
    validateLimits(request);
    validateConditionalSpecificFields(request);
  }

  // 회당 금액 · 주식 수 가운데 하나만, 예산 · 최대 횟수는 비우거나 0 보다 큼
  private void validateLimits(ConditionalStrategyRequest request) {
    if ((request.getAmountPerPurchase() == null) == (request.getSharesPerPurchase() == null)) {
      throw new BacktestException(BacktestResponse.AMOUNT_OR_SHARES_REQUIRED);
    }
    if (request.getAmountPerPurchase() != null && !Decimals.isPositive(request.getAmountPerPurchase())) {
      throw new BacktestException(BacktestResponse.STRATEGY_AMOUNT_PER_PURCHASE_REQUIRED);
    }
    BacktestRequestValidator.requireShares(request.getSharesPerPurchase());
    if (request.getTotalInvestment() != null && !Decimals.isPositive(request.getTotalInvestment())) {
      throw new BacktestException(BacktestResponse.INVALID_TOTAL_INVESTMENT);
    }
    if (request.getMaxPurchases() != null && request.getMaxPurchases() <= 0) {
      throw new BacktestException(BacktestResponse.INVALID_MAX_PURCHASES);
    }
  }

  private void validateConditionalSpecificFields(ConditionalStrategyRequest request) {
    if (request.getDropPercentage() == null
      || !Decimals.isPositive(request.getDropPercentage())) {
      throw new BacktestException(BacktestResponse.STRATEGY_DROP_PERCENTAGE_REQUIRED);
    }
    BacktestRequestValidator.requireSellRates(request.getTakeProfitRate(), request.getStopLossRate());
    BacktestRequestValidator.requireTrailingStop(request.getTrailingStopRate(), request.getTrailingSellRate());
    BacktestRequestValidator.requirePartialTakeProfits(request.getPartialTakeProfits());
    // 재투자 주식은 루프 뒤에 더해져 매도와 순서가 맞지 않음
    if (sells(request) && Boolean.TRUE.equals(request.getReinvestDividends())) {
      throw new BacktestException(BacktestResponse.SELL_WITH_REINVEST);
    }
  }

  // 익절 · 손절 · 트레일링 스탑 · 부분 익절 가운데 하나라도 켰는지
  private static boolean sells(ConditionalStrategyRequest request) {
    return request.getTakeProfitRate() != null || request.getStopLossRate() != null
      || request.getTrailingStopRate() != null
      || (request.getPartialTakeProfits() != null && !request.getPartialTakeProfits().isEmpty());
  }

  // 양도세와 연말 정리를 둘 다 켰는지
  private static boolean harvests(ConditionalStrategyRequest request) {
    return YearEndHarvest.enabled(request.getCapitalGainsTax(), request.getYearEndHarvest());
  }

  // 최대 횟수(null 이면 제한 없음)에 닿았는지
  private static boolean reached(Integer maxPurchases, int rounds) {
    return maxPurchases != null && rounds >= maxPurchases;
  }

  // 남은 예산(null 이면 제한 없음)을 넘는 금액인지
  private static boolean exceeds(BigDecimal amount, BigDecimal budgetLeft) {
    return budgetLeft != null && amount.compareTo(budgetLeft) > 0;
  }

  // 조건부 매수 실행(시세가 깨진 날 종가 0 이하 · 계산이 실패한 날은 사지 않고 이유를 남긴 뒤 다음 날 다시)
  private StrategyPurchases executeConditionalPurchases(
    ConditionalStrategyRequest request, LocalDate actualEndDate, CapitalGainsTaxPayer taxPayer) {

    List<StrategyTransaction> transactions = new ArrayList<>();
    BigDecimal previousClose = null;

    //  BULK API 사용: 전체 기간의 가격 데이터를 한 번에 조회
    log.info("조건부 매수 전략 - Bulk 데이터 조회 시작: {} ~ {}", request.getStartDate(), actualEndDate);
    java.util.Map<LocalDate, OHLCPriceDto> priceMap = BacktestDataUtils.buildPriceMap(
      marketData, request.getSymbol(), request.getStartDate(), actualEndDate);
    if (priceMap.values().stream().noneMatch(PriceLookup::isUsable)) {
      throw new BacktestException(BacktestResponse.DATA_NOT_FOUND,
        "조건부 매수 전략 실행 가능한 데이터가 없습니다");
    }

    //  BULK API 사용: 전체 기간의 환율 데이터를 한 번에 조회 (수동 설정이 없는 경우만)
    FxRates fxRateMap = FxRates.EMPTY;
    if (request.getPurchaseFxRate() == null) {
      // 첫 시세일부터 평가일까지
      LocalDate firstPrice = priceMap.keySet().stream().min(LocalDate::compareTo).orElse(actualEndDate);
      fxRateMap = BacktestDataUtils.buildDailyFxRateMap(marketData, firstPrice, actualEndDate);
      log.info("환율 데이터 조회 완료: {}개", fxRateMap.size());
    }

    LocalDate currentDate = request.getStartDate();
    Integer maxPurchases = request.getMaxPurchases();
    BigDecimal budget = request.getTotalInvestment();
    int purchaseCount = 0;
    // 넣은 돈(KRW) · 예산 부족으로 사지 못한 회차 수와 그 첫 날짜
    BigDecimal deposited = BigDecimal.ZERO;
    int shortRounds = 0;
    LocalDate shortFrom = null;
    List<SkippedRound> skippedRounds = new ArrayList<>();
    boolean sells = sells(request);
    TakeProfitStopLoss exits = TakeProfitStopLoss.of(request.getTakeProfitRate(), request.getStopLossRate());
    TrailingStop trailing = TrailingStop.of(request.getTrailingStopRate(), request.getTrailingSellRate());
    TakeProfitLadder ladder = TakeProfitLadder.of(request.getPartialTakeProfits());
    // 보유 주식 · 그 원가(USD, 매수 수수료 포함) · 판 돈(USD)
    BigDecimal heldShares = BigDecimal.ZERO;
    BigDecimal heldCost = BigDecimal.ZERO;
    BigDecimal cash = BigDecimal.ZERO;
    // 매도 규칙을 켠 요청의 연말 정리 날
    Set<LocalDate> harvestDates = sells && harvests(request)
      ? YearEndHarvest.dates(priceMap, request.getStartDate(), actualEndDate)
      : Set.of();

    // 메모리에서 데이터 조회 (API 호출 없음), 파는 규칙을 켜면 새 돈 회차를 다 쓴 뒤에도 끝까지 봄
    while (!currentDate.isAfter(actualEndDate) && (sells || !reached(maxPurchases, purchaseCount + shortRounds))) {
      OHLCPriceDto priceData = priceMap.get(currentDate);
      boolean usable = PriceLookup.isUsable(priceData);
      try {
        if (priceData != null && priceData.available() && !usable) {
          skippedRounds.add(new SkippedRound(currentDate, SkipReason.NO_PRICE));
        } else if (usable) {
          BigDecimal fx = fxOn(request, fxRateMap, currentDate);
          // 양도세 낼 현금이 모자라면 그 날짜 종가에 모자란 만큼 팖(하루 매도 한 번에 세지 않고 그 날짜 매수도 봄)
          SaleShare taxExit = TaxSale.sells(request.getTaxSale())
            ? SaleShare.forTax(taxPayer.shortfallUsd(currentDate, transactions, cash, fx),
              PriceLookup.effectiveClose(priceData), request.getTradingFeeRate(), heldShares, heldCost)
            : null;
          if (taxExit != null) {
            StrategyTransaction taxSale = sale(request, priceData, currentDate, taxExit.shares(), taxExit.cost(),
              PriceLookup.effectiveClose(priceData), taxExit.trigger(), fxRateMap);
            transactions.add(taxSale);
            cash = cash.add(taxSale.getProceeds());
            heldShares = heldShares.subtract(taxExit.shares());
            heldCost = heldCost.subtract(taxExit.cost());
          }
          StrategyTransaction payment = taxPayer.pay(currentDate, transactions, cash, fx,
            fxRateMap.isDefault(currentDate));
          if (payment != null) {
            transactions.add(payment);
            cash = cash.subtract(payment.getTaxPaid());
          }
          // 익절 · 손절 → 트레일링 스탑 → 부분 익절 차례로 보고 판 날은 사지 않음(그 날짜에 산 주식은 매도를 본 뒤라 팔지 않음)
          StrategyTransaction sale = sells && heldShares.signum() > 0
            ? sellIfTriggered(request, priceData, currentDate, heldShares, heldCost, fxRateMap, exits)
            : null;
          if (sale == null && heldShares.signum() > 0) {
            sale = trailingSale(request, priceData, currentDate, heldShares, heldCost, fxRateMap, trailing);
          }
          if (sale == null && heldShares.signum() > 0) {
            sale = ladderSale(request, priceData, currentDate, heldShares, heldCost, fxRateMap, ladder);
          }
          if (sale != null) {
            transactions.add(sale);
            cash = cash.add(sale.getProceeds());
            heldShares = heldShares.add(sale.getShares());
            // 판 몫의 원가(판 돈 - 실현 손익)를 뺌
            heldCost = heldCost.subtract(sale.getProceeds().subtract(sale.getRealizedProfit()));
            log.debug("조건부 매도 실행: {} - {} ({}주, 실현 손익 ${})",
              currentDate, sale.getTrigger(), sale.getShares().negate(), sale.getRealizedProfit());
          } else {
            var purchaseDecision = previousClose == null
              ? NO_BUY
              : evaluatePurchaseCondition(previousClose, priceData, request.getDropPercentage(),
                PriceDirection.orDrop(request.getDirection()));

            boolean roundsUsed = reached(maxPurchases, purchaseCount + shortRounds);
            // 처음 예산이 모자란 날짜부터는 새 돈을 넣지 않음
            BigDecimal budgetLeft = budget == null ? null : shortFrom != null ? BigDecimal.ZERO : budget.subtract(deposited);
            StrategyTransaction transaction = purchaseDecision.shouldBuy()
              ? buy(request, priceData, currentDate, purchaseDecision, fxRateMap, cash, roundsUsed, budgetLeft)
              : null;
            if (purchaseDecision.shouldBuy() && transaction == null && !roundsUsed) {
              shortRounds++;
              shortFrom = shortFrom == null ? currentDate : shortFrom;
            }
            if (transaction != null) {
              transactions.add(transaction);
              if (Decimals.isPositive(transaction.getAmount())) {
                purchaseCount++;
                deposited = deposited.add(transaction.getAmount());
              }
              if (transaction.getCashUsed() != null) {
                cash = cash.subtract(transaction.getCashUsed());
              }
              if (heldShares.signum() == 0) {
                trailing.open(transaction.getPrice(), PriceLookup.effectiveClose(priceData));
                ladder.open();
              }
              heldShares = heldShares.add(transaction.getShares());
              heldCost = heldCost.add(transaction.costUsd());
              log.debug("조건부 매수 실행: {} - {} ({}원 투자, {}주 매수)",
                currentDate, purchaseDecision.trigger(), transaction.getAmount(), transaction.getShares());
            }
          }
          // 그 날짜 사고팔기 뒤 연말 정리
          YearEndHarvest.Trade harvest = harvestDates.contains(currentDate) && heldShares.signum() > 0
            ? YearEndHarvest.single(currentDate, transactions, PriceLookup.effectiveClose(priceData), heldShares,
              heldCost, request.getTradingFeeRate(), fx, fxRateMap.isDefault(currentDate))
            : null;
          if (harvest != null) {
            transactions.add(harvest.sale());
            transactions.add(harvest.rebuy());
            heldShares = heldShares.add(harvest.sharesChange());
            heldCost = heldCost.add(harvest.costChange());
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

    log.info("조건부 매수 전략 완료: {}회 매수, 사지 못한 날 {}, 예산 부족 {}회",
      transactions.size(), skippedRounds.size(), shortRounds);
    SkippedSpan shortfall = shortFrom == null ? null : new SkippedSpan(shortFrom, shortRounds, SkipReason.OVER_BUDGET);
    return new StrategyPurchases(transactions, skippedRounds, priceMap, fxRateMap, shortfall);
  }

  // 회당 금액 · 주식 수만큼 사되 판 돈을 먼저 쓰고 모자란 만큼만 새로 넣음
  // 횟수를 다 썼거나 새로 넣을 돈이 남은 예산을 넘으면 판 돈으로만(판 돈이 없거나 한 주도 못 사면 null)
  private StrategyTransaction buy(ConditionalStrategyRequest request, OHLCPriceDto priceData, LocalDate date,
    PurchaseDecision decision, FxRates fxRateMap, BigDecimal cash, boolean roundsUsed, BigDecimal budgetLeft) {
    BigDecimal fxRate = fxOn(request, fxRateMap, date);
    Fill fill = request.getSharesPerPurchase() != null
      ? fillShares(request, decision.price(), fxRate, cash, roundsUsed, budgetLeft)
      : fillAmount(request, decision.price(), fxRate, cash, roundsUsed, budgetLeft);
    if (fill == null) {
      return null;
    }
    return StrategyTransaction.builder()
      .date(date)
      .actualDate(priceData.date())
      .price(decision.price())
      .shares(fill.shares())
      .fee(fill.fee())
      .amount(fill.deposit())
      .cashUsed(fill.cashUsed())
      .fxRate(fxRate)
      .defaultFxDate(fxRateMap.isDefault(date) ? date : null)
      .trigger(decision.trigger())
      .build();
  }

  // 한 번 사는 주식 수 · 수수료(USD) · 새로 넣는 돈(KRW) · 쓰는 판 돈(USD, 새 돈만이면 null)
  private record Fill(BigDecimal shares, BigDecimal fee, BigDecimal deposit, BigDecimal cashUsed) {}

  // 회당 금액만큼, 판 돈으로만 살 때는 판 돈만큼
  private static Fill fillAmount(ConditionalStrategyRequest request, BigDecimal price, BigDecimal fxRate,
    BigDecimal cash, boolean roundsUsed, BigDecimal budgetLeft) {
    BigDecimal amount = request.getAmountPerPurchase();
    if (!Decimals.isPositive(cash)) {
      if (roundsUsed || exceeds(amount, budgetLeft)) {
        return null;
      }
      // 수수료를 차감하고 남은 달러로 매수
      RoundPurchase purchase = RoundPurchase.byAmount(amount, price, fxRate, request.getTradingFeeRate());
      return new Fill(purchase.shares(), purchase.feeUsd(), amount, null);
    }
    BigDecimal needUsd = amount.divide(fxRate, 8, RoundingMode.HALF_UP);
    BigDecimal topUp = needUsd.compareTo(cash) > 0
      ? amount.subtract(cash.multiply(fxRate)).setScale(2, RoundingMode.HALF_UP)
      : BigDecimal.ZERO;
    boolean cashOnly = roundsUsed || exceeds(topUp, budgetLeft);
    BigDecimal budgetUsd = cashOnly ? needUsd.min(cash) : needUsd;
    BigDecimal fee = feeOn(budgetUsd, request.getTradingFeeRate());
    return new Fill(budgetUsd.subtract(fee).divide(price, 8, RoundingMode.HALF_UP), fee,
      cashOnly ? BigDecimal.ZERO : topUp, cash.min(budgetUsd));
  }

  // 주식 수만큼(매수가 × 주식 수 + 수수료), 판 돈으로만 살 때는 판 돈으로 살 수 있는 주식 수까지
  private static Fill fillShares(ConditionalStrategyRequest request, BigDecimal price, BigDecimal fxRate,
    BigDecimal cash, boolean roundsUsed, BigDecimal budgetLeft) {
    boolean hasCash = Decimals.isPositive(cash);
    if (!hasCash && roundsUsed) {
      return null;
    }
    int shares = request.getSharesPerPurchase();
    RoundPurchase full = RoundPurchase.byShares(shares, price, fxRate, request.getTradingFeeRate());
    if (!hasCash) {
      return exceeds(full.amountKrw(), budgetLeft) ? null : new Fill(full.shares(), full.feeUsd(), full.amountKrw(), null);
    }
    if (full.costUsd().compareTo(cash) <= 0) {
      return new Fill(full.shares(), full.feeUsd(), BigDecimal.ZERO, full.costUsd());
    }
    BigDecimal topUp = MoneyUtils.convertUsdToKrw(full.costUsd().subtract(cash), fxRate);
    if (!roundsUsed && !exceeds(topUp, budgetLeft)) {
      return new Fill(full.shares(), full.feeUsd(), topUp, cash);
    }
    RoundPurchase partial = RoundPurchase.withinUsd(shares, cash, price, fxRate, request.getTradingFeeRate());
    return partial == null ? null : new Fill(partial.shares(), partial.feeUsd(), BigDecimal.ZERO, partial.costUsd());
  }

  // 보유분 평균 단가(수수료 포함)의 익절 · 손절 기준가에 닿으면 그 값에 전량 매도(아니면 null)
  private StrategyTransaction sellIfTriggered(ConditionalStrategyRequest request, OHLCPriceDto priceData,
    LocalDate date, BigDecimal shares, BigDecimal cost, FxRates fxRateMap, TakeProfitStopLoss exits) {
    TakeProfitStopLoss.Exit exit = exits.check(priceData, shares, cost);
    return exit == null ? null : sale(request, priceData, date, shares, cost, exit.price(), exit.trigger(), fxRateMap);
  }

  // 그 날짜 종가가 트레일링 스탑 기준가 × (1 - 하락 비율) 이하면 보유의 팔 비율만큼 그 종가에 팔고 판 값을 새 기준가로(아니면 null)
  private StrategyTransaction trailingSale(ConditionalStrategyRequest request, OHLCPriceDto priceData, LocalDate date,
    BigDecimal shares, BigDecimal cost, FxRates fxRateMap, TrailingStop trailing) {
    BigDecimal close = PriceLookup.effectiveClose(priceData);
    SaleShare exit = trailing.check(close, shares, cost);
    if (exit == null) {
      return null;
    }
    StrategyTransaction sale = sale(request, priceData, date, exit.shares(), exit.cost(), close, exit.trigger(),
      fxRateMap);
    trailing.sold(close);
    return sale;
  }

  // 그 날짜 종가가 부분 익절 단계에 닿으면 보유의 단계 팔 비율만큼 그 종가에 팔고 그 단계를 씀(아니면 null)
  private StrategyTransaction ladderSale(ConditionalStrategyRequest request, OHLCPriceDto priceData, LocalDate date,
    BigDecimal shares, BigDecimal cost, FxRates fxRateMap, TakeProfitLadder ladder) {
    BigDecimal close = PriceLookup.effectiveClose(priceData);
    SaleShare exit = ladder.check(close, shares, cost);
    if (exit == null) {
      return null;
    }
    StrategyTransaction sale = sale(request, priceData, date, exit.shares(), exit.cost(), close, exit.trigger(),
      fxRateMap);
    ladder.sold();
    return sale;
  }

  // 그 날짜 정한 값에 정한 주식을 팔고 매도 수수료를 뺀 판 돈 · 판 몫의 원가 대비 실현 손익을 남김
  private StrategyTransaction sale(ConditionalStrategyRequest request, OHLCPriceDto priceData, LocalDate date,
    BigDecimal shares, BigDecimal cost, BigDecimal price, String trigger, FxRates fxRateMap) {
    BigDecimal gross = shares.multiply(price).setScale(8, RoundingMode.HALF_UP);
    BigDecimal fee = feeOn(gross, request.getTradingFeeRate());
    BigDecimal proceeds = gross.subtract(fee);
    return StrategyTransaction.builder()
      .date(date)
      .actualDate(priceData.date())
      .price(price)
      .shares(shares.negate())
      .fee(fee)
      .amount(BigDecimal.ZERO)
      .proceeds(proceeds)
      .realizedProfit(proceeds.subtract(cost))
      .fxRate(fxOn(request, fxRateMap, date))
      .defaultFxDate(Boolean.TRUE.equals(request.getCapitalGainsTax()) && fxRateMap.isDefault(date) ? date : null)
      .trigger(trigger)
      .build();
  }

  // 그 날짜 환율(수동 환율이 있으면 그 값)
  private static BigDecimal fxOn(ConditionalStrategyRequest request, FxRates fxRateMap, LocalDate date) {
    return request.getPurchaseFxRate() != null ? request.getPurchaseFxRate() : fxRateMap.get(date);
  }

  // 거래 달러 × 수수료율(USD 두 자리, 수수료율이 없으면 0)
  private static BigDecimal feeOn(BigDecimal usd, BigDecimal feeRate) {
    return feeRate != null && Decimals.isPositive(feeRate)
      ? usd.multiply(feeRate).setScale(2, RoundingMode.HALF_UP)
      : BigDecimal.ZERO;
  }

  // 방향에 맞는 기준가를 넘은 날 매수, 둘 다 닿은 날은 시가부터 하락 기준가 아래면 하락 · 아니면 상승 기준가
  private PurchaseDecision evaluatePurchaseCondition(BigDecimal previousClose, OHLCPriceDto price,
    BigDecimal percentage, PriceDirection direction) {
    BigDecimal drop = direction == PriceDirection.RISE
      ? null : IntradayCross.price(previousClose, price, percentage, false);
    BigDecimal rise = direction == PriceDirection.DROP
      ? null : IntradayCross.price(previousClose, price, percentage, true);
    boolean rising = rise != null && !IntradayCross.atOpen(drop, price);
    BigDecimal buyPrice = rising ? rise : drop;
    return buyPrice == null
      ? NO_BUY
      : new PurchaseDecision(true, buyPrice, IntradayCross.label(previousClose, buyPrice, rising ? "상승" : "하락"));
  }
}
