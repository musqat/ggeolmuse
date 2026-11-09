package com.muscat.trade.common.enums.type;

import java.time.LocalDate;
import java.util.Arrays;

// 수익 흐름 기간(ALL 은 첫 거래일부터)
public enum FlowPeriod {
  ONE_MONTH("1M", 30),
  THREE_MONTHS("3M", 90),
  ONE_YEAR("1Y", 365),
  ALL("ALL", 0);

  private final String code;
  private final int days;

  FlowPeriod(String code, int days) {
    this.code = code;
    this.days = days;
  }

  // 1M · 3M · 1Y · ALL 이 아니면 IllegalArgumentException
  public static FlowPeriod of(String code) {
    return Arrays.stream(values())
      .filter(period -> period.code.equals(code))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("기간은 1M · 3M · 1Y · ALL 중 하나: " + code));
  }

  // 기간 첫 날짜(그 날짜 포함, ALL 은 null)
  public LocalDate startFrom(LocalDate today) {
    return this == ALL ? null : today.minusDays(days);
  }
}
