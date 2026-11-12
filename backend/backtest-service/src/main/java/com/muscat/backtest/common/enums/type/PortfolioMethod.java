package com.muscat.backtest.common.enums.type;

// 포트폴리오 매수 방식
public enum PortfolioMethod {
  LUMP_SUM,  // 줄마다 기간 안 첫 거래일에 금액 전부
  DCA;       // 줄마다 적립식 회차일에 회당 금액

  // 비우면 한 번에 사기
  public static PortfolioMethod orLumpSum(PortfolioMethod method) {
    return method != null ? method : LUMP_SUM;
  }
}
