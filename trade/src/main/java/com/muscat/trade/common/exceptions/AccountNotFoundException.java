package com.muscat.trade.common.exceptions;

import com.muscat.trade.common.enums.BaseResponseEnum;

public class AccountNotFoundException extends TradeException {
  
  public AccountNotFoundException(String accountId) {
    super(BaseResponseEnum.ACCOUNT_NOT_FOUND, "계좌를 찾을 수 없습니다: " + accountId);
  }
  
  public AccountNotFoundException() {
    super(BaseResponseEnum.ACCOUNT_NOT_FOUND);
  }
}