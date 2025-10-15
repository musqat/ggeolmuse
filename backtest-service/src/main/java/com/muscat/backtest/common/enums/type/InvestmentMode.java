package com.muscat.backtest.common.enums.type;

// 조건부 매수 투자 모드
public enum InvestmentMode {
  TOTAL_BUDGET,      // 총 예산 분할: 총 투자금을 회당 투자금으로 나눠서 분할 매수
  PER_PURCHASE       // 회당 고정 금액: 조건 만족 시마다 지정 금액 투자 (최대 횟수 제한)
}
