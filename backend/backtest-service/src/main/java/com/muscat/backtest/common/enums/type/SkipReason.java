package com.muscat.backtest.common.enums.type;

// 회차를 사지 못한 이유
public enum SkipReason {
  NO_PRICE,           // 시세가 없음(종가 0 이하 포함)
  CALCULATION_ERROR   // 회차 계산이 실패함
}
