package com.muscat.backtest.common.enums.type;

// 전략 거래 종류
public enum TransactionKind {
  BUY,                    // 새 돈 · 현금으로 매수
  SELL,                   // 매도(신호 · 익절 · 손절 · 양도세 · 리밸런싱 · 기간 끝)
  TAX_PAYMENT,            // 양도세 납부(수량 0)
  DIVIDEND_REINVESTMENT,  // 배당 재투자
  HARVEST_SELL,           // 연말 정리 매도
  HARVEST_BUY;            // 연말 정리 재매수

  // 수량이 줄어드는 거래
  public boolean sells() {
    return this == SELL || this == HARVEST_SELL;
  }

  // 연말 정리 매도 · 재매수
  public boolean harvests() {
    return this == HARVEST_SELL || this == HARVEST_BUY;
  }
}
