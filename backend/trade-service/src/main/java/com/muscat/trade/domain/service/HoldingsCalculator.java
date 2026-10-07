package com.muscat.trade.domain.service;

import com.muscat.trade.common.constants.TradeConstants;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.Trade;

// 체결 하나를 보유에 반영. 매수는 가중 평균, 매도는 투자금액을 판 비율만큼 줄인다
public final class HoldingsCalculator {

  // holdings 가 null 이면 보유 없음. uncovered 는 보유보다 많이 판 매도
  public record Applied(Holdings holdings, boolean uncovered) {
  }

  private HoldingsCalculator() {
  }

  // current 를 직접 바꾼다. 보유가 없을 때 매수면 새 보유를 만든다
  public static Applied apply(Holdings current, Trade trade, int pricePrecision) {
    if (trade.getTradeType() == TradeType.BUY) {
      if (current == null) {
        return new Applied(Holdings.builder()
          .userId(trade.getUserId())
          .accountId(trade.getAccountId())
          .symbol(trade.getSymbol())
          .totalQuantity(trade.getQuantity())
          .avgPurchasePrice(trade.getPrice())
          .totalInvestedAmount(trade.getTotalAmount())
          .build(), false);
      }
      current.addPurchase(trade.getQuantity(), trade.getPrice(), pricePrecision);
      return new Applied(current, false);
    }

    if (current == null || current.getTotalQuantity().compareTo(trade.getQuantity()) < 0) {
      return new Applied(null, true);
    }
    if (current.getTotalQuantity().compareTo(trade.getQuantity()) == 0) {
      return new Applied(null, false);
    }
    current.sellShares(trade.getQuantity(), TradeConstants.SELL_RATIO_PRECISION);
    return new Applied(current, false);
  }
}
