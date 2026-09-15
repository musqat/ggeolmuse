package com.muscat.user.common.exceptions;

import com.muscat.commonlib.exception.BusinessException;
import com.muscat.user.common.enums.responses.UserResponse;

/**
 * 인증 관련 예외
 */
public class AuthenticationException extends BusinessException {

  // UserResponse를 받는 편의 생성자
  public AuthenticationException(UserResponse response) {
    super(response);
  }

  // UserResponse + Custom Message
  public AuthenticationException(UserResponse response, String customMessage) {
    super(response, customMessage);
  }

  // UserResponse + Cause
  public AuthenticationException(UserResponse response, Throwable cause) {
    super(response, cause);
  }
}
