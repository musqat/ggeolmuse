package com.muscat.user.common.exception;

import com.muscat.commonlib.exception.BusinessException;
import com.muscat.user.common.enums.response.UserResponse;

/**
 * 인증 관련 예외
 */
public class AuthenticationException extends BusinessException {

  public AuthenticationException(UserResponse response) {
    super(response);
  }

  public AuthenticationException(UserResponse response, Throwable cause) {
    super(response, cause);
  }
}
