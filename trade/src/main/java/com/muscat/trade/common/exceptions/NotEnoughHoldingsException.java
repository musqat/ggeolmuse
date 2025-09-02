package com.muscat.trade.common.exceptions;

import com.muscat.trade.common.enums.BaseResponseEnum;
import java.math.BigDecimal;

public class NotEnoughHoldingsException extends TradeException {
  
  public NotEnoughHoldingsException(String symbol, BigDecimal required, BigDecimal available) {
    super(BaseResponseEnum.INSUFFICIENT_HOLDINGS, 
          String.format("보유 수량이 부족합니다. 종목: %s, 필요수량: %s, 보유수량: %s", 
                       symbol, required, available));
  }
  
  public NotEnoughHoldingsException() {
    super(BaseResponseEnum.INSUFFICIENT_HOLDINGS);
  }
}