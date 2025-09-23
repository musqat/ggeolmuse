package com.muscat.commonlib.exception;

import lombok.Getter;

@Getter
public class BaseException extends RuntimeException {

  private final String errorCode;
  private final String errorMessage;

  public BaseException(String errorCode, String errorMessage) {
    super(errorMessage);
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
  }

  public BaseException(String errorCode, String errorMessage, Throwable cause) {
    super(errorMessage, cause);
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
  }
}