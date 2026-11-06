package com.muscat.backtest.common.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Function;

// 날짜별 원화 환율 경로
public final class FxPath {

  private FxPath() {
  }

  // 시장 환율: 그날 이하 가장 늦은 기록, 첫 기록 전 날은 첫 기록, 기록이 없으면 fallback 하나
  public static Function<LocalDate, BigDecimal> market(Map<LocalDate, BigDecimal> records, BigDecimal fallback) {
    NavigableMap<LocalDate, BigDecimal> known = new TreeMap<>(records);
    return date -> {
      Map.Entry<LocalDate, BigDecimal> floor = known.floorEntry(date);
      if (floor != null) {
        return floor.getValue();
      }
      Map.Entry<LocalDate, BigDecimal> ceiling = known.ceilingEntry(date);
      return ceiling != null ? ceiling.getValue() : fallback;
    };
  }

  // 시작일 환율에서 평가일 환율까지 날짜에 비례해 이은 환율(기간 밖은 양끝 값)
  public static Function<LocalDate, BigDecimal> interpolated(LocalDate start, BigDecimal startRate, LocalDate end,
    BigDecimal endRate) {
    long span = ChronoUnit.DAYS.between(start, end);
    return date -> {
      if (span <= 0 || !date.isAfter(start)) {
        return startRate;
      }
      if (!date.isBefore(end)) {
        return endRate;
      }
      BigDecimal ratio = BigDecimal.valueOf(ChronoUnit.DAYS.between(start, date))
        .divide(BigDecimal.valueOf(span), MathContext.DECIMAL64);
      return startRate.add(endRate.subtract(startRate).multiply(ratio));
    };
  }
}
