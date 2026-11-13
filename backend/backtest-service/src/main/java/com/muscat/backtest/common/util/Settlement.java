package com.muscat.backtest.common.util;

import java.time.DayOfWeek;
import java.time.LocalDate;

// 판 거래의 결제일: 체결일 뒤 1 평일(2024-05-28 전은 2 평일), 주말만 건너뛰고 휴장일은 평일로 셈
public final class Settlement {

  // 결제가 T+1 인 첫 거래일(그 앞은 T+2)
  private static final LocalDate T_PLUS_ONE_FROM = LocalDate.of(2024, 5, 28);

  private Settlement() {}

  // 그 날짜에 판 거래의 결제일
  public static LocalDate date(LocalDate tradeDate) {
    LocalDate day = tradeDate;
    for (int left = tradeDate.isBefore(T_PLUS_ONE_FROM) ? 2 : 1; left > 0; left--) {
      day = day.plusDays(1);
      while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
        day = day.plusDays(1);
      }
    }
    return day;
  }
}
