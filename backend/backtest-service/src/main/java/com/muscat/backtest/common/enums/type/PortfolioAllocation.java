package com.muscat.backtest.common.enums.type;

// 포트폴리오 줄 금액을 정하는 방식
public enum PortfolioAllocation {
  AMOUNT,  // 줄마다 원화 금액
  WEIGHT;  // 총액 × 줄마다 비율(%)

  // 비우면 금액
  public static PortfolioAllocation orAmount(PortfolioAllocation allocation) {
    return allocation != null ? allocation : AMOUNT;
  }
}
