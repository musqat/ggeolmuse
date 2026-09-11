package com.muscat.marketdata.domain.service;

import java.math.BigDecimal;

/**
 * 분할로 과거 종가가 바뀌었는지 판정한다.
 */
public final class SplitScale {

  // 가장 작은 분할 5:4 의 배수. 이보다 작은 차이는 분할로 보지 않는다
  private static final double MIN_LOG = Math.log(1.25);

  private SplitScale() {
  }

  /** 두 종가가 1.25배 이상 차이 나는지. */
  public static boolean isScaleChange(BigDecimal prev, BigDecimal fresh) {
    if (!positive(prev) || !positive(fresh)) {
      return false;
    }
    return Math.abs(Math.log(prev.doubleValue() / fresh.doubleValue())) >= MIN_LOG;
  }

  private static boolean positive(BigDecimal value) {
    return value != null && value.signum() > 0;
  }
}
