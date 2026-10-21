package com.muscat.trade.common.exception;

import com.muscat.commonlib.exception.BusinessException;
import com.muscat.trade.common.enums.response.TradeResponse;

public class TradeException extends BusinessException {

  public TradeException(TradeResponse response) {
    super(response);
  }

  public TradeException(TradeResponse response, Throwable cause) {
    super(response, cause);
  }
}
