package com.muscat.backtest.common.enums.type;

// 이동평균 교차 · 밴드 신호의 체결 시점
public enum ExecutionTiming {
  SAME_DAY_CLOSE,  // 신호 난 날 종가
  NEXT_DAY_OPEN;   // 신호 다음 거래일 시가

  // 비우면 신호 난 날 종가
  public static ExecutionTiming orSameDayClose(ExecutionTiming timing) {
    return timing != null ? timing : SAME_DAY_CLOSE;
  }

  // 다음 거래일 시가에 체결하는지(비우면 신호 난 날 종가)
  public static boolean nextDayOpen(ExecutionTiming timing) {
    return timing == NEXT_DAY_OPEN;
  }
}
