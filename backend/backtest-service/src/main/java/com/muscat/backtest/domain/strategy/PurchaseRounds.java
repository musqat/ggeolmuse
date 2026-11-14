package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.enums.type.SkipReason;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.domain.model.SkippedSpan;
import java.math.BigDecimal;
import java.time.LocalDate;

// 회차 · 예산 셈: 새 돈을 넣은 회차 수 · 넣은 돈(KRW)과 예산 부족으로 사지 못한 회차 수 · 처음 못 산 날짜
final class PurchaseRounds {

  private final Integer maxPurchases;
  private final BigDecimal budget;
  private int purchased;
  private BigDecimal deposited = BigDecimal.ZERO;
  private int shortRounds;
  private LocalDate shortFrom;

  // 최대 횟수 · 예산(null 이면 제한 없음)
  PurchaseRounds(Integer maxPurchases, BigDecimal budget) {
    this.maxPurchases = maxPurchases;
    this.budget = budget;
  }

  // 남은 예산(null 이면 제한 없음)을 넘는 금액인지
  static boolean exceeds(BigDecimal amount, BigDecimal budgetLeft) {
    return budgetLeft != null && amount.compareTo(budgetLeft) > 0;
  }

  // 남은 예산을 넘는 금액인지
  boolean exceeds(BigDecimal amount) {
    return exceeds(amount, budgetLeft());
  }

  // 산 회차와 예산 부족 회차를 더해 최대 횟수(null 이면 제한 없음)에 닿았는지
  boolean used() {
    return maxPurchases != null && purchased + shortRounds >= maxPurchases;
  }

  // 남은 예산(null 이면 제한 없음), 처음 예산이 모자란 날짜부터는 새 돈을 넣지 않음
  BigDecimal budgetLeft() {
    return budget == null ? null : shortFrom != null ? BigDecimal.ZERO : budget.subtract(deposited);
  }

  // 새 돈(KRW)을 넣은 매수면 회차 · 넣은 돈에 셈
  void bought(BigDecimal deposit) {
    if (Decimals.isPositive(deposit)) {
      purchased++;
      deposited = deposited.add(deposit);
    }
  }

  // 예산이 모자라 사지 못한 회차
  void shortOn(LocalDate date) {
    shortRounds++;
    shortFrom = shortFrom == null ? date : shortFrom;
  }

  // 예산이 한 번이라도 모자랐는지
  boolean isShort() {
    return shortFrom != null;
  }

  BigDecimal deposited() {
    return deposited;
  }

  int shortRounds() {
    return shortRounds;
  }

  // 예산 부족 구간(없으면 null)
  SkippedSpan span() {
    return shortFrom == null ? null : new SkippedSpan(shortFrom, shortRounds, SkipReason.OVER_BUDGET);
  }
}
