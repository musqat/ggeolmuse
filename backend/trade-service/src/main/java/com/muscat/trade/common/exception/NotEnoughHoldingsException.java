package com.muscat.trade.common.exception;

import com.muscat.trade.common.enums.response.TradeResponse;

public class NotEnoughHoldingsException extends TradeException {

  public NotEnoughHoldingsException() {
    super(TradeResponse.INSUFFICIENT_HOLDINGS);
  }
  
}