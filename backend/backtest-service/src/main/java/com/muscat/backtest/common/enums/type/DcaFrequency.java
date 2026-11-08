package com.muscat.backtest.common.enums.type;

// 적립식 매수 주기와 그 거래에 남기는 매수 이유
public enum DcaFrequency {
  DAILY("매일 정액"),    // 첫 매수일부터 거래일마다
  WEEKLY("매주 정액"),   // 첫 매수일의 요일마다
  MONTHLY("월정액");     // 투자 주기(개월)마다 투자일에

  private final String trigger;

  DcaFrequency(String trigger) {
    this.trigger = trigger;
  }

  public String trigger() {
    return trigger;
  }
}
