package com.muscat.user.common.exceptions;

import com.muscat.commonlib.exception.BaseException;
import com.muscat.user.common.enums.responses.AccountHistoryResponse;
import org.springframework.http.HttpStatus;

/**
 * 계좌 거래 내역 관련 예외
 */
public class AccountHistoryException extends BaseException {

  private final HttpStatus httpStatus;

  public AccountHistoryException(AccountHistoryResponse response) {
    super(response.getCode(), response.getMessage());
    this.httpStatus = response.getHttpStatus();
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }
}
