package com.muscat.trade.common.enums.type;

// 체결 상태
public enum TradeStatus {
  PENDING,   // 잔액 정산을 기다림
  COMPLETED, // 체결됨
  CANCELLED  // 잔액 반영 실패로 취소됨
}
