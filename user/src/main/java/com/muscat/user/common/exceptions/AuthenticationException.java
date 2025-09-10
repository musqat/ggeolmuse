package com.muscat.user.common.exceptions;

import com.muscat.commonlib.exception.BaseException;
import com.muscat.user.common.enums.responses.UserResponse;
import org.springframework.http.HttpStatus;

/**
 * 인증 관련 예외
 */
public class AuthenticationException extends BaseException {

  private final HttpStatus httpStatus;

  // UserResponse를 받는 편의 생성자
  public AuthenticationException(UserResponse response) {
    super(response.getCode(), response.getMessage());
    this.httpStatus = response.getHttpStatus();
  }

  // UserResponse + Custom Message
  public AuthenticationException(UserResponse response, String customMessage) {
    super(response.getCode(), customMessage);
    this.httpStatus = response.getHttpStatus();
  }

  // UserResponse + Cause
  public AuthenticationException(UserResponse response, Throwable cause) {
    super(response.getCode(), response.getMessage(), cause);
    this.httpStatus = response.getHttpStatus();
  }

  public AuthenticationException(String statusCode, String message) {
    super(statusCode, message);
    this.httpStatus = HttpStatus.UNAUTHORIZED;
  }

  public AuthenticationException(String statusCode, String message, HttpStatus httpStatus) {
    super(statusCode, message);
    this.httpStatus = httpStatus;
  }

  public AuthenticationException(String statusCode, String message, Throwable cause) {
    super(statusCode, message, cause);
    this.httpStatus = HttpStatus.UNAUTHORIZED;
  }

  public AuthenticationException(String statusCode, String message, HttpStatus httpStatus, Throwable cause) {
    super(statusCode, message, cause);
    this.httpStatus = httpStatus;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }
}
