package com.muscat.backtest.common.enums.type;

// 포트폴리오 거래 이유
public enum TradeReason {
  BUY,                    // 한 번에 매수
  DCA_BUY,                // 적립식 회차 매수
  PERIOD_END_SALE,        // 기간 마지막 거래일 전량 매도
  DIVIDEND_REINVESTMENT,  // 배당 재투자
  TAX_PAYMENT,            // 양도세 납부
  REBALANCE_SELL,         // 리밸런싱으로 목표 비중까지 매도
  REBALANCE_BUY,          // 리밸런싱으로 목표 비중까지 매수
  TAKE_PROFIT,            // 평균 단가 대비 익절 기준가에 전량 매도
  STOP_LOSS,              // 평균 단가 대비 손절 기준가에 전량 매도
  STRATEGY_BUY,           // 이동평균 · 밴드 줄의 매수 신호에 줄 몫 현금 전부로 매수
  STRATEGY_SELL,          // 이동평균 · 밴드 줄의 매도 신호에 전량 매도
  TAX_SALE,               // 양도세 낼 현금이 모자라 판매 방식대로 매도
  HARVEST_GAIN_SELL,      // 연말에 공제를 채우도록 이익 난 종목 매도
  HARVEST_GAIN_BUY,       // 공제 채우기로 판 돈 전부로 같은 값에 다시 매수
  HARVEST_LOSS_SELL,      // 연말에 실현 이익을 공제까지 내리도록 손실 난 종목 매도
  HARVEST_LOSS_BUY        // 손실 상계로 판 돈 전부로 같은 값에 다시 매수
}
