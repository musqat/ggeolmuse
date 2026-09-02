package com.muscat.user.common.exceptions;

import com.muscat.commonlib.exception.BaseException;
import com.muscat.user.common.enums.responses.UserResponse;
import org.springframework.http.HttpStatus;

public class UserException extends BaseException {

  private final HttpStatus httpStatus;

  public UserException(UserResponse response) {
    super(response.getCode(), response.getMessage());
    this.httpStatus = response.getHttpStatus();
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }
}


