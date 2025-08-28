package com.muscat.user.common.exceptions;

import com.muscat.user.common.enums.BaseResponseEnum;

/**
 * 계좌 거래 내역 관련 예외
 */
public class AccountHistoryException extends BusinessException {

  public AccountHistoryException(BaseResponseEnum errorCode) {
    super(errorCode);
  }

  public AccountHistoryException(BaseResponseEnum errorCode, String customMessage) {
    super(errorCode, customMessage);
  }

  public AccountHistoryException(BaseResponseEnum errorCode, Throwable cause) {
    super(errorCode, cause);
  }
}
