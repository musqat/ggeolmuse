package com.muscat.trade.common.exceptions;

import com.muscat.trade.common.responses.TradeResponse;

public class AccountNotFoundException extends TradeException {
  
  public AccountNotFoundException(String accountId) {
    super(TradeResponse.ACCOUNT_NOT_FOUND, "계좌를 찾을 수 없습니다: " + accountId);
  }
  
  public AccountNotFoundException() {
    super(TradeResponse.ACCOUNT_NOT_FOUND);
  }
}