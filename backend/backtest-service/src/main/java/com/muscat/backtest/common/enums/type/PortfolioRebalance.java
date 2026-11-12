package com.muscat.backtest.common.enums.type;

import java.time.LocalDate;

// 포트폴리오 리밸런싱 주기
public enum PortfolioRebalance {
  NONE(0),       // 안 함
  YEARLY(12),    // 해마다
  QUARTERLY(3),  // 분기마다
  MONTHLY(1);    // 달마다

  private final int months;

  PortfolioRebalance(int months) {
    this.months = months;
  }

  // 비우면 안 함
  public static PortfolioRebalance orNone(PortfolioRebalance rebalance) {
    return rebalance != null ? rebalance : NONE;
  }

  public boolean enabled() {
    return months > 0;
  }

  // 그 날짜가 든 주기 번호(1월부터 주기 길이 개월씩 나눈 차례)
  public int period(LocalDate date) {
    return (date.getYear() * 12 + date.getMonthValue() - 1) / months;
  }
}
