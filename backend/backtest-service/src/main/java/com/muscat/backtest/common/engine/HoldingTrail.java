package com.muscat.backtest.common.engine;

import com.muscat.backtest.common.calculation.StrategyCalculator;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.domain.model.HoldingStep;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.function.Function;

// 단일 매수의 보유 단계 · 양도세 계산 거래 기록: 연말 정리 날마다 당일 종가에 연말 정리(날이 null 이면 연말 정리 끔)
final class HoldingTrail {

  private final NavigableSet<LocalDate> dates;
  private final Map<LocalDate, OHLCPriceDto> priceMap;
  private final BigDecimal feeRate;
  private final Function<LocalDate, BigDecimal> fx;
  private final List<StrategyTransaction> taxTransactions = new ArrayList<>();
  private final List<HoldingStep> steps = new ArrayList<>();
  private final List<StrategyTransaction> trades = new ArrayList<>();

  HoldingTrail(NavigableSet<LocalDate> dates, Map<LocalDate, OHLCPriceDto> priceMap, BigDecimal feeRate,
    Function<LocalDate, BigDecimal> fx) {
    this.dates = dates;
    this.priceMap = priceMap;
    this.feeRate = feeRate;
    this.fx = fx;
  }

  List<HoldingStep> steps() {
    return steps;
  }

  List<StrategyTransaction> taxTransactions() {
    return taxTransactions;
  }

  // 받은 날짜 환율
  BigDecimal fxOn(LocalDate date) {
    return fx.apply(date);
  }

  // 받은 날짜부터 주식 수 · 현금(USD)
  void step(LocalDate from, BigDecimal shares, BigDecimal cash) {
    steps.add(new HoldingStep(from, shares, cash));
  }

  // 양도세 계산 거래
  void taxTrade(StrategyTransaction tx) {
    taxTransactions.add(tx);
  }

  // before 앞 연말 정리 날마다 연말 정리한 뒤 주식 수(현금은 그대로, 쓴 날은 다시 쓰지 않음)
  BigDecimal harvestBefore(LocalDate before, BigDecimal shares, BigDecimal cash) {
    BigDecimal held = shares;
    while (dates != null && !dates.isEmpty() && dates.first().isBefore(before)) {
      LocalDate date = dates.pollFirst();
      YearEndHarvest.Trade trade = YearEndHarvest.single(date, taxTransactions,
        PriceLookup.effectiveClose(priceMap.get(date)), held, StrategyCalculator.holdingCost(taxTransactions),
        feeRate, fx.apply(date), false);
      if (trade != null) {
        List.of(trade.sale(), trade.rebuy()).forEach(tx -> {
          taxTransactions.add(tx);
          trades.add(tx);
        });
        held = held.add(trade.sharesChange());
        steps.add(new HoldingStep(date, held, cash));
      }
    }
    return held;
  }

  // 연말 정리 매도 · 재매수 수수료 합(USD)
  BigDecimal harvestFees() {
    return trades.stream().map(StrategyTransaction::getFee).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  // 연말 정리 거래와 기간 끝 매도(끄면 null)
  List<StrategyTransaction> harvestTransactions(StrategyTransaction endSale) {
    if (dates == null) {
      return null;
    }
    List<StrategyTransaction> all = new ArrayList<>(trades);
    if (endSale != null) {
      all.add(endSale);
    }
    return all;
  }
}
