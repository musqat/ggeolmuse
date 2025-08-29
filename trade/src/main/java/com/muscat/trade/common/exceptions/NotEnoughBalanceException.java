package com.muscat.trade.common.exceptions;

import com.muscat.trade.common.responses.TradeResponse;
import java.math.BigDecimal;

public class NotEnoughBalanceException extends TradeException {
  
  public NotEnoughBalanceException(BigDecimal required, BigDecimal available) {
    super(TradeResponse.INSUFFICIENT_BALANCE, 
          String.format("잔액이 부족합니다. 필요금액: %s, 보유금액: %s", required, available));
  }
  
  public NotEnoughBalanceException() {
    super(TradeResponse.INSUFFICIENT_BALANCE);
  }
}