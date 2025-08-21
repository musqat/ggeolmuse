package com.muscat.user.common.exceptions;

import com.muscat.user.common.enums.BaseResponseEnum;

/**
 * 소셜 로그인 관련 예외
 */
public class SocialLoginException extends BusinessException {

  public SocialLoginException(BaseResponseEnum errorCode) {
    super(errorCode);
  }

  public SocialLoginException(BaseResponseEnum errorCode, String customMessage) {
    super(errorCode, customMessage);
  }

  public SocialLoginException(BaseResponseEnum errorCode, Throwable cause) {
    super(errorCode, cause);
  }
}

