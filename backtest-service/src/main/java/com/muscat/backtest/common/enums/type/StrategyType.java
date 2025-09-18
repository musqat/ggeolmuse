package com.muscat.backtest.common.enums.type;

// 백테스트 투자 전략 타입
public enum StrategyType {
  DCA,                    // 적립식 투자 (정액 분할 매수)
  CONDITIONAL_PURCHASE    // 조건부 매수 (하락장 물타기)
}