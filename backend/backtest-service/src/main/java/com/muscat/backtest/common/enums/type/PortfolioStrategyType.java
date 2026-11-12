package com.muscat.backtest.common.enums.type;

// 포트폴리오 종목 줄 전략
public enum PortfolioStrategyType {
  LUMP_SUM,  // 기간 안 그 종목 첫 거래일에 금액 전부
  DCA;       // 요청의 적립식 주기로 회차일에 회당 금액

  // 줄 전략이 비면 요청의 매수 방식
  public static PortfolioStrategyType orMethod(PortfolioStrategyType type, PortfolioMethod method) {
    if (type != null) {
      return type;
    }
    return PortfolioMethod.orLumpSum(method) == PortfolioMethod.DCA ? DCA : LUMP_SUM;
  }
}
