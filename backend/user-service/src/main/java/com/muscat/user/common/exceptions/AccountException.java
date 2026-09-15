package com.muscat.user.common.exceptions;

import com.muscat.commonlib.exception.BusinessException;
import com.muscat.user.common.enums.responses.AccountResponse;

public class AccountException extends BusinessException {

  public AccountException(AccountResponse response) {
    super(response);
  }
}
