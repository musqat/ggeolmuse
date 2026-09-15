package com.muscat.backtest.common.exception;

import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.commonlib.exception.BusinessException;

public class BacktestException extends BusinessException {

  public BacktestException(BacktestResponse errorCode) {
    super(errorCode);
  }

  public BacktestException(BacktestResponse errorCode, String details) {
    super(errorCode, details);
  }
}
