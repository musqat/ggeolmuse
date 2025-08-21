package com.muscat.user.common.exceptions;

import com.muscat.user.common.enums.BaseResponseEnum;

/**
 * 사용자 관련 예외
 */
public class UserException extends BusinessException {

  public UserException(BaseResponseEnum errorCode) {
    super(errorCode);
  }

  public UserException(BaseResponseEnum errorCode, String customMessage) {
    super(errorCode, customMessage);
  }

  public UserException(BaseResponseEnum errorCode, Throwable cause) {
    super(errorCode, cause);
  }
}


