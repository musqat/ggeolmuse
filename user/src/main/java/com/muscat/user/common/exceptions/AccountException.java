package com.muscat.user.common.exceptions;

import com.muscat.commonlib.exception.BaseException;
import com.muscat.user.common.enums.responses.AccountResponse;
import org.springframework.http.HttpStatus;

/**
 * 계좌 관련 예외
 */
public class AccountException extends BaseException {

  private final HttpStatus httpStatus;

  public AccountException(AccountResponse response) {
    super(response.getCode(), response.getMessage());
    this.httpStatus = response.getHttpStatus();
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }
}