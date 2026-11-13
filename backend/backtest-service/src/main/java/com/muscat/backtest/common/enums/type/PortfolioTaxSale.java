package com.muscat.backtest.common.enums.type;

// 포트폴리오에서 양도세 낼 현금이 모자랄 때 파는 방식
public enum PortfolioTaxSale {
  NONE,           // 현금이 생길 때 냄
  TARGET_WEIGHT,  // 목표 비중보다 커진 종목부터 넘친 만큼, 그래도 모자라면 목표 비중대로 나눠 팖
  CUSTOM_ORDER,   // 종목 줄마다 정한 판매 순서대로 팖
  LOSS_FIRST,     // 평가 손익이 작은(손실 큰) 종목부터 팖
  GAIN_NEUTRAL;   // 손실 · 이익 종목을 섞어 판 이익 합이 0 이 되게 팖

  // 비우면 팔지 않음
  public static PortfolioTaxSale orNone(PortfolioTaxSale taxSale) {
    return taxSale != null ? taxSale : NONE;
  }
}
