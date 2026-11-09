package com.muscat.backtest.common.enums.type;

// 기간이 끝날 때 남은 주식 처리
public enum PeriodEnd {
  HOLD,         // 끝까지 보유
  SELL_AT_END;  // 기간 마지막 거래일 종가에 전량 매도

  // 기간 끝에 파는지(비우면 끝까지 보유)
  public static boolean sells(PeriodEnd periodEnd) {
    return periodEnd == SELL_AT_END;
  }
}
