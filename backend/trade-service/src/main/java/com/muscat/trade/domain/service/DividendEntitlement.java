package com.muscat.trade.domain.service;

import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.Trade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// 배당을 받을 수량. 배당락일 전날 장 마감 때 가진 수량이 기준이라, 배당락일 전(거래일 < 배당락일)에 체결된 매수 합 − 매도 합이다.
// 배당락일 당일 · 이후의 매수는 못 받고, 배당락일 당일 · 이후의 매도는 받는다
public final class DividendEntitlement {

  private DividendEntitlement() {
  }

  // 한 계좌의 거래들로 배당락일 전날 보유 수량을 구한다
  public static BigDecimal sharesHeldBefore(List<Trade> trades, LocalDate exDate) {
    return trades.stream()
      .filter(trade -> trade.getStatus() == TradeStatus.COMPLETED && trade.getTradeDate().isBefore(exDate))
      .map(trade -> trade.getTradeType() == TradeType.SELL ? trade.getQuantity().negate() : trade.getQuantity())
      .reduce(BigDecimal.ZERO, BigDecimal::add)
      .max(BigDecimal.ZERO);
  }
}
