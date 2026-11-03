package com.muscat.backtest.common.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

// 날짜별 환율과 그중 환율이 없어 기본 환율로 채운 날
public record FxRates(Map<LocalDate, BigDecimal> rates, Set<LocalDate> defaulted) {

  public static final FxRates EMPTY = new FxRates(Map.of(), Set.of());

  public BigDecimal get(LocalDate date) {
    return rates.get(date);
  }

  public boolean isDefault(LocalDate date) {
    return defaulted.contains(date);
  }

  public int size() {
    return rates.size();
  }
}
