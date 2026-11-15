package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.engine.CapitalGainsTaxPayer;
import com.muscat.backtest.common.engine.TradeLedger;
import com.muscat.backtest.common.enums.type.PriceDirection;
import com.muscat.backtest.common.util.FxRates;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.model.PurchaseDecision;
import com.muscat.backtest.domain.model.SkippedSpan;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

// 조건부 매수 한 번 돌리기: 모의 계좌 · 보유 · 회차와 매도 규칙 · 연말 정리 날을 두고 쓸 수 있는 일봉마다 사고팖
@Slf4j
final class ConditionalRun {

  private static final PurchaseDecision NO_BUY = new PurchaseDecision(false, null, "");

  private final ConditionalStrategyRequest request;
  private final CapitalGainsTaxPayer taxPayer;
  private final Set<LocalDate> harvestDates;
  private final boolean sells;
  private final TradeLedger ledger;
  private final TradeLedger.Holding holding = new TradeLedger.Holding();
  private final PurchaseRounds rounds;
  private final CashFirstPurchase sizing;
  private final TakeProfitStopLoss exits;
  private final PartialExits partials;

  // sells: 익절 · 손절 · 트레일링 스탑 · 부분 익절 가운데 하나라도 켰는지
  ConditionalRun(ConditionalStrategyRequest request, CapitalGainsTaxPayer taxPayer, FxRates fxRates,
    Set<LocalDate> harvestDates, boolean sells) {
    this.request = request;
    this.taxPayer = taxPayer;
    this.harvestDates = harvestDates;
    this.sells = sells;
    this.ledger = new TradeLedger(request.getTradingFeeRate(), request.getPurchaseFxRate(), fxRates,
      Boolean.TRUE.equals(request.getCapitalGainsTax()));
    this.rounds = new PurchaseRounds(request.getMaxPurchases(), request.getTotalInvestment());
    this.sizing = CashFirstPurchase.of(request);
    this.exits = TakeProfitStopLoss.of(request.getTakeProfitRate(), request.getStopLossRate());
    this.partials = PartialExits.of(request.getTrailingStopRate(), request.getTrailingSellRate(),
      request.getPartialTakeProfits());
  }

  // 더 볼 날이 있는지(파는 규칙을 켰으면 새 돈 회차를 다 쓴 뒤에도 끝까지)
  boolean active() {
    return sells || !rounds.used();
  }

  List<StrategyTransaction> transactions() {
    return ledger.transactions();
  }

  int shortRounds() {
    return rounds.shortRounds();
  }

  // 예산 부족 구간(없으면 null)
  SkippedSpan span() {
    return rounds.span();
  }

  // 쓸 수 있는 일봉 하루: 양도세 → 매도 규칙 → (판 날이 아니면) 조건 매수 → 연말 정리
  void day(LocalDate date, OHLCPriceDto priceData, BigDecimal previousClose) {
    BigDecimal close = PriceLookup.effectiveClose(priceData);
    // 양도세 낼 현금이 모자라면 당일 종가에 모자란 만큼 팔고 냄(하루 매도 한 번에 세지 않고 당일 매수도 봄)
    ledger.sellAndPayTax(taxPayer, request.getTaxSale(), holding, date, close);
    StrategyTransaction sale = sell(date, priceData, close);
    if (sale != null) {
      log.debug("조건부 매도 실행: {} - {} ({}주, 실현 손익 ${})",
        date, sale.getTrigger(), sale.getShares().negate(), sale.getRealizedProfit());
    } else {
      buyOnCondition(date, priceData, close, previousClose);
    }
    // 당일 사고팔기 뒤 연말 정리
    if (harvestDates.contains(date)) {
      ledger.harvestSingle(holding, date, close);
    }
  }

  // 익절 · 손절 → 트레일링 스탑 → 부분 익절 차례로 본 매도(당일 산 주식은 매도를 본 뒤라 팔지 않음, 팔지 않으면 null)
  private StrategyTransaction sell(LocalDate date, OHLCPriceDto priceData, BigDecimal close) {
    StrategyTransaction sale = sells && holding.held() ? sellIfTriggered(date, priceData) : null;
    if (sale == null && holding.held()) {
      sale = partialSale(date, close);
    }
    return sale;
  }

  // 전 거래일 종가 대비 조건에 맞으면 사고 예산이 모자라 못 산 회차를 셈
  private void buyOnCondition(LocalDate date, OHLCPriceDto priceData, BigDecimal close, BigDecimal previousClose) {
    var purchaseDecision = previousClose == null
      ? NO_BUY
      : evaluatePurchaseCondition(previousClose, priceData, request.getDropPercentage(),
        PriceDirection.orDrop(request.getDirection()));
    boolean roundsUsed = rounds.used();
    StrategyTransaction transaction = purchaseDecision.shouldBuy()
      ? buy(priceData, date, purchaseDecision, roundsUsed)
      : null;
    if (purchaseDecision.shouldBuy() && transaction == null && !roundsUsed) {
      rounds.shortOn(date);
    }
    if (transaction != null) {
      if (!holding.held()) {
        partials.open(transaction.getPrice(), close);
      }
      ledger.buy(holding, transaction);
      rounds.bought(transaction.getAmount());
      log.debug("조건부 매수 실행: {} - {} ({}원 투자, {}주 매수)",
        date, purchaseDecision.trigger(), transaction.getAmount(), transaction.getShares());
    }
  }

  // 보유분 평균 단가(수수료 포함)의 익절 · 손절 기준가에 닿으면 기준가에 전량 매도(아니면 null)
  private StrategyTransaction sellIfTriggered(LocalDate date, OHLCPriceDto priceData) {
    TakeProfitStopLoss.Exit exit = exits.check(priceData, holding.shares(), holding.cost());
    return exit == null
      ? null
      : ledger.sell(holding, date, date, exit.price(), holding.shares(), holding.cost(), exit.trigger(), null);
  }

  // 당일 종가가 트레일링 스탑이나 부분 익절에 닿으면 정한 몫을 당일 종가에 팖(아니면 null)
  private StrategyTransaction partialSale(LocalDate date, BigDecimal close) {
    SaleShare exit = partials.check(close, holding.shares(), holding.cost());
    if (exit == null) {
      return null;
    }
    StrategyTransaction sale = ledger.sell(holding, date, date, close, exit.shares(), exit.cost(), exit.trigger(),
      null);
    partials.sold(close);
    return sale;
  }

  // 조건 매수가 · 당일 환율로 판 돈 먼저 쓰는 매수 거래(못 사면 null)
  private StrategyTransaction buy(OHLCPriceDto priceData, LocalDate date, PurchaseDecision decision,
    boolean roundsUsed) {
    BigDecimal fxRate = ledger.fxOn(date);
    CashFirstPurchase.Fill fill = sizing.fill(decision.price(), fxRate, ledger.cash(), roundsUsed,
      rounds.budgetLeft());
    if (fill == null) {
      return null;
    }
    return StrategyTransaction.buy(new StrategyTransaction.Order(null, null, date, priceData.date(), decision.price(),
      fxRate, ledger.defaultFxDate(date), decision.trigger()), fill.shares(), fill.fee(), fill.deposit(), fill.cashUsed());
  }

  // 방향에 맞는 기준가를 넘은 날 매수, 둘 다 닿은 날은 시가부터 하락 기준가 아래면 하락 · 아니면 상승 기준가
  private static PurchaseDecision evaluatePurchaseCondition(BigDecimal previousClose, OHLCPriceDto price,
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
