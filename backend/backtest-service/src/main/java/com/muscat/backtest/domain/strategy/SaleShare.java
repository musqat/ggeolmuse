package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.constants.BacktestConstants.Money;
import com.muscat.backtest.common.constants.BacktestConstants.TransactionTrigger;
import com.muscat.backtest.common.enums.type.TaxSale;
import java.math.BigDecimal;
import java.math.RoundingMode;

// 매도 규칙이 정한 팔 주식 수 · 그 몫의 원가(USD) · 전량인지 · 거래 이유
record SaleShare(BigDecimal shares, BigDecimal cost, boolean all, String trigger) {

  // 보유 × 팔 비율(소수 여덟째 반올림, 1 이상이면 전량)과 같은 비율의 원가, 반올림해 0 주면 null
  static SaleShare of(BigDecimal held, BigDecimal cost, BigDecimal rate, String trigger) {
    BigDecimal shares = rate.compareTo(BigDecimal.ONE) >= 0
      ? held
      : held.multiply(rate).setScale(Money.SHARES_SCALE, RoundingMode.HALF_UP);
    return shares.signum() == 0 ? null : portion(held, cost, shares, trigger);
  }

  // 양도세 낼 현금이 모자란 달러만큼 그 값에 팔 주식(보유 상한)과 같은 비율의 원가, 모자라지 않거나 보유가 없으면 null
  static SaleShare forTax(BigDecimal shortfallUsd, BigDecimal price, BigDecimal feeRate, BigDecimal held,
    BigDecimal cost) {
    if (shortfallUsd.signum() <= 0 || held.signum() <= 0) {
      return null;
    }
    return portion(held, cost, TaxSale.shares(shortfallUsd, price, feeRate, held), TransactionTrigger.TAX_SALE);
  }

  // 보유 가운데 팔 주식과 같은 비율의 원가(전량이면 원가 전부)
  private static SaleShare portion(BigDecimal held, BigDecimal cost, BigDecimal shares, String trigger) {
    boolean all = shares.compareTo(held) >= 0;
    BigDecimal soldCost = all ? cost : cost.multiply(shares).divide(held, Money.SHARES_SCALE, RoundingMode.HALF_UP);
    return new SaleShare(all ? held : shares, soldCost, all, trigger);
  }
}
