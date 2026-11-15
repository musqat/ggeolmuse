package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.domain.calculation.BacktestCalculationUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.domain.calculation.RoundPurchase;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;

// 판 돈 먼저 쓰는 매수: 회당 금액 · 주식 수만큼 사되 판 돈을 먼저 쓰고 모자란 만큼만 새로 넣음
// 횟수를 다 썼거나 새로 넣을 돈이 남은 예산을 넘으면 판 돈으로만(판 돈이 없거나 한 주도 못 사면 null)
final class CashFirstPurchase {

  // 한 번 사는 주식 수 · 수수료(USD) · 새로 넣는 돈(KRW) · 쓰는 판 돈(USD, 새 돈만이면 null)
  record Fill(BigDecimal shares, BigDecimal fee, BigDecimal deposit, BigDecimal cashUsed) {}

  private final BigDecimal amountPerPurchase;
  private final Integer sharesPerPurchase;
  private final BigDecimal feeRate;

  CashFirstPurchase(BigDecimal amountPerPurchase, Integer sharesPerPurchase, BigDecimal feeRate) {
    this.amountPerPurchase = amountPerPurchase;
    this.sharesPerPurchase = sharesPerPurchase;
    this.feeRate = feeRate;
  }

  static CashFirstPurchase of(ConditionalStrategyRequest request) {
    return new CashFirstPurchase(request.getAmountPerPurchase(), request.getSharesPerPurchase(),
      request.getTradingFeeRate());
  }

  // 받은 가격 · 환율 · 판 돈(USD)으로 한 번 사는 양(못 사면 null)
  Fill fill(BigDecimal price, BigDecimal fxRate, BigDecimal cash, boolean roundsUsed, BigDecimal budgetLeft) {
    return sharesPerPurchase != null
      ? byShares(price, fxRate, cash, roundsUsed, budgetLeft)
      : byAmount(price, fxRate, cash, roundsUsed, budgetLeft);
  }

  // 회당 금액만큼, 판 돈으로만 살 때는 판 돈만큼
  private Fill byAmount(BigDecimal price, BigDecimal fxRate, BigDecimal cash, boolean roundsUsed,
    BigDecimal budgetLeft) {
    BigDecimal amount = amountPerPurchase;
    if (!Decimals.isPositive(cash)) {
      if (roundsUsed || PurchaseRounds.exceeds(amount, budgetLeft)) {
        return null;
      }
      // 수수료를 차감하고 남은 달러로 매수
      RoundPurchase purchase = RoundPurchase.byAmount(amount, price, fxRate, feeRate);
      return new Fill(purchase.shares(), purchase.feeUsd(), amount, null);
    }
    BigDecimal needUsd = amount.divide(fxRate, 8, RoundingMode.HALF_UP);
    BigDecimal topUp = needUsd.compareTo(cash) > 0
      ? amount.subtract(cash.multiply(fxRate)).setScale(2, RoundingMode.HALF_UP)
      : BigDecimal.ZERO;
    boolean cashOnly = roundsUsed || PurchaseRounds.exceeds(topUp, budgetLeft);
    BigDecimal budgetUsd = cashOnly ? needUsd.min(cash) : needUsd;
    BigDecimal fee = BacktestCalculationUtils.calculateTradingFee(budgetUsd, feeRate);
    return new Fill(budgetUsd.subtract(fee).divide(price, 8, RoundingMode.HALF_UP), fee,
      cashOnly ? BigDecimal.ZERO : topUp, cash.min(budgetUsd));
  }

  // 주식 수만큼(매수가 × 주식 수 + 수수료), 판 돈으로만 살 때는 판 돈으로 살 수 있는 주식 수까지
  private Fill byShares(BigDecimal price, BigDecimal fxRate, BigDecimal cash, boolean roundsUsed,
    BigDecimal budgetLeft) {
    boolean hasCash = Decimals.isPositive(cash);
    if (!hasCash && roundsUsed) {
      return null;
    }
    int shares = sharesPerPurchase;
    RoundPurchase full = RoundPurchase.byShares(shares, price, fxRate, feeRate);
    if (!hasCash) {
      return PurchaseRounds.exceeds(full.amountKrw(), budgetLeft)
        ? null
        : new Fill(full.shares(), full.feeUsd(), full.amountKrw(), null);
    }
    if (full.costUsd().compareTo(cash) <= 0) {
      return new Fill(full.shares(), full.feeUsd(), BigDecimal.ZERO, full.costUsd());
    }
    BigDecimal topUp = MoneyUtils.convertUsdToKrw(full.costUsd().subtract(cash), fxRate);
    if (!roundsUsed && !PurchaseRounds.exceeds(topUp, budgetLeft)) {
      return new Fill(full.shares(), full.feeUsd(), topUp, cash);
    }
    RoundPurchase partial = RoundPurchase.withinUsd(shares, cash, price, fxRate, feeRate);
    return partial == null ? null : new Fill(partial.shares(), partial.feeUsd(), BigDecimal.ZERO, partial.costUsd());
  }
}
