package com.muscat.user.common.exceptions;

import com.muscat.commonlib.exception.BaseException;
import com.muscat.user.common.enums.responses.SocialResponse;
import org.springframework.http.HttpStatus;

public class SocialLoginException extends BaseException {

  private final HttpStatus httpStatus;

  public SocialLoginException(SocialResponse response) {
    super(response.getCode(), response.getMessage());
    this.httpStatus = response.getHttpStatus();
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }
}

