package com.muscat.backtest.common.enums.type;

// 백테스트 투자 전략 타입
public enum StrategyType {
  SIMPLE,                 // 단순 일시 매수 (Simple Simulation)
  DCA,                    // 적립식 투자 (회당 금액 · 주식 수 분할 매수)
  CONDITIONAL_PURCHASE,   // 조건부 매수 (전 거래일 종가 대비 장중 하락 · 상승 시)
  MA_CROSS,               // 이동평균 교차 (골든 크로스에 사고 데드 크로스에 팜)
  MA_BAND                 // 이동평균 밴드 (평균 위 선 위에서 사고 아래 선 아래에서 팜)
}