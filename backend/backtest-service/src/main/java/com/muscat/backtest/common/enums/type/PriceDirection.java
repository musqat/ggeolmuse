package com.muscat.backtest.common.enums.type;

// 조건부 매수가 보는 전 거래일 종가 대비 방향
public enum PriceDirection {
  DROP("하락"),
  RISE("상승"),
  BOTH("하락 · 상승");

  private final String label;

  PriceDirection(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  public static PriceDirection orDrop(PriceDirection direction) {
    return direction == null ? DROP : direction;
  }
}
