package com.muscat.backtest.common.enums.type;

// 백테스트 투자 전략 타입
public enum StrategyType {
  SIMPLE,                 // 단순 일시 매수 (Simple Simulation)
  DCA,                    // 적립식 투자 (정액 분할 매수)
  CONDITIONAL_PURCHASE    // 조건부 매수 (하락장 물타기)
}