package com.muscat.backtest.common.util;

import java.math.BigDecimal;

// BigDecimal 부호 비교 헬퍼(null 이면 NPE)
public final class Decimals {

  private Decimals() {}

  // 0보다 큼 (양수)
  public static boolean isPositive(BigDecimal v) {
    return v.signum() > 0;
  }

  // 0
  public static boolean isZero(BigDecimal v) {
    return v.signum() == 0;
  }

  // 0보다 작음 (음수)
  public static boolean isNegative(BigDecimal v) {
    return v.signum() < 0;
  }
}
