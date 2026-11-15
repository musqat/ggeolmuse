package com.muscat.backtest.common.enums.type;

// 적립식 매수 주기와 주기마다 거래에 남기는 매수 이유
public enum DcaFrequency {
  DAILY("매일 정액", "매일"),    // 첫 매수일부터 거래일마다
  WEEKLY("매주 정액", "매주"),   // 첫 매수일의 요일마다
  MONTHLY("월정액", "매달");     // 투자 주기(개월)마다 투자일에

  private final String trigger;
  private final String every;

  DcaFrequency(String trigger, String every) {
    this.trigger = trigger;
    this.every = every;
  }

  // 비우면 매달
  public static DcaFrequency orMonthly(DcaFrequency frequency) {
    return frequency != null ? frequency : MONTHLY;
  }

  public String trigger() {
    return trigger;
  }

  // 주기 이름("매일" · "매주" · "매달", 매달에서 투자 주기가 2개월 이상이면 "3개월마다")
  public String every(int interval) {
    return this == MONTHLY && interval > 1 ? interval + "개월마다" : every;
  }

  // 주식 수로 살 때의 매수 이유("매달 2주", 매달에서 투자 주기가 2개월 이상이면 "3개월마다 2주")
  public String trigger(int shares, int interval) {
    return every(interval) + " " + shares + "주";
  }
}
