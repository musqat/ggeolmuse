package com.muscat.user.common.exception;

import com.muscat.commonlib.exception.BusinessException;
import com.muscat.user.common.enums.response.AccountHistoryResponse;

public class AccountHistoryException extends BusinessException {

  public AccountHistoryException(AccountHistoryResponse response) {
    super(response);
  }
}
