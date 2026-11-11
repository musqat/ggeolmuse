package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.constants.BacktestConstants.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;

// 매도 규칙이 정한 팔 주식 수 · 그 몫의 원가(USD) · 전량인지 · 거래 이유
record SaleShare(BigDecimal shares, BigDecimal cost, boolean all, String trigger) {

  // 보유 × 팔 비율(소수 여덟째 반올림, 1 이상이면 전량)과 같은 비율의 원가, 반올림해 0 주면 null
  static SaleShare of(BigDecimal held, BigDecimal cost, BigDecimal rate, String trigger) {
    BigDecimal shares = rate.compareTo(BigDecimal.ONE) >= 0
      ? held
      : held.multiply(rate).setScale(Money.SHARES_SCALE, RoundingMode.HALF_UP);
    if (shares.signum() == 0) {
      return null;
    }
    boolean all = shares.compareTo(held) >= 0;
    BigDecimal soldCost = all ? cost : cost.multiply(shares).divide(held, Money.SHARES_SCALE, RoundingMode.HALF_UP);
    return new SaleShare(all ? held : shares, soldCost, all, trigger);
  }
}
