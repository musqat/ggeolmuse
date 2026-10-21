package com.muscat.user.common.exception;

import com.muscat.commonlib.exception.BusinessException;
import com.muscat.user.common.enums.response.AccountResponse;

public class AccountException extends BusinessException {

  public AccountException(AccountResponse response) {
    super(response);
  }

  public AccountException(AccountResponse response, String message) {
    super(response, message);
  }
}
