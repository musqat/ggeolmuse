package com.muscat.backtest.common.enums.type;

// 백테스트 투자 전략 타입
public enum StrategyType {
  SIMPLE,                 // 단순 일시 매수 (Simple Simulation)
  DCA,                    // 적립식 투자 (회당 금액 · 주식 수 분할 매수)
  CONDITIONAL_PURCHASE    // 조건부 매수 (전 거래일 종가 대비 장중 하락 · 상승 시)
}