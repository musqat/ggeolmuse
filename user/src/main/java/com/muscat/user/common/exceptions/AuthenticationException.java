package com.muscat.user.common.exceptions;

import com.muscat.user.common.enums.BaseResponseEnum;

/**
 * 인증 관련 예외
 */
public class AuthenticationException extends BusinessException {

  public AuthenticationException(BaseResponseEnum errorCode) {
    super(errorCode);
  }

  public AuthenticationException(BaseResponseEnum errorCode, String customMessage) {
    super(errorCode, customMessage);
  }

  public AuthenticationException(BaseResponseEnum errorCode, Throwable cause) {
    super(errorCode, cause);
  }
}
