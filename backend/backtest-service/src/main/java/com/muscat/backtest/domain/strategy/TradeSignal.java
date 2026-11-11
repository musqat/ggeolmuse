package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.domain.dto.response.SignalChart;
import java.time.LocalDate;

// 일봉마다 사기 · 팔기를 정하는 매매 신호와 그 값의 차트
interface TradeSignal {

  // i 번째 일봉의 신호(매수 BUY · 매도 SELL, 없거나 신호 기간이 안 찼으면 null)
  Action at(int i);

  // 시작일부터 일봉마다 차트 점과 교차한 날
  SignalChart chart(String symbol, LocalDate start);

  String buyTrigger();

  String sellTrigger();

  enum Action { BUY, SELL }
}
