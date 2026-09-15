package com.muscat.user.common.exceptions;

import com.muscat.commonlib.exception.BusinessException;
import com.muscat.user.common.enums.responses.AccountHistoryResponse;

public class AccountHistoryException extends BusinessException {

  public AccountHistoryException(AccountHistoryResponse response) {
    super(response);
  }
}
