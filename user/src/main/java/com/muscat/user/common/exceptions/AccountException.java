package com.muscat.user.common.exceptions;

import com.muscat.user.common.enums.BaseResponseEnum;

/**
 * 계좌 관련 예외
 */
public class AccountException extends BusinessException {

  public AccountException(BaseResponseEnum errorCode) {
    super(errorCode);
  }

  public AccountException(BaseResponseEnum errorCode, String customMessage) {
    super(errorCode, customMessage);
  }

  public AccountException(BaseResponseEnum errorCode, Throwable cause) {
    super(errorCode, cause);
  }
}