package com.muscat.commonlib.exception;

public class ServiceException extends BaseException {

  public ServiceException(String errorCode, String errorMessage) {
    super(errorCode, errorMessage);
  }

  public ServiceException(String errorCode, String errorMessage, Throwable cause) {
    super(errorCode, errorMessage, cause);
  }
}