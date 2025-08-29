package com.muscat.trade.common.exceptions;

import com.muscat.trade.common.enums.BaseResponseEnum;

/**
 * 거래 관련 예외
 */
public class TradeException extends BusinessException {

  public TradeException(BaseResponseEnum errorCode) {
    super(errorCode);
  }

  public TradeException(BaseResponseEnum errorCode, String customMessage) {
    super(errorCode, customMessage);
  }

  public TradeException(BaseResponseEnum errorCode, Throwable cause) {
    super(errorCode, cause);
  }
}